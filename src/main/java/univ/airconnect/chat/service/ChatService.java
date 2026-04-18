package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.dto.response.ChatParticipantDetailResponse;
import univ.airconnect.chat.dto.response.ChatParticipantProfileResponse;
import univ.airconnect.chat.dto.response.ChatRoomListUpdateResponse;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final RedisMessageListenerContainer redisMessageListener;
    private final RedisSubscriber redisSubscriber;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserBlockPolicyService userBlockPolicyService;

    private final Map<String, ChannelTopic> topics = new ConcurrentHashMap<>();

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    private static final String CHAT_SESSION_KEY = "chat:session:";
    private static final String SESSION_ROOM_KEY = "chat:session-room:";
    private static final String SESSION_SUBSCRIPTION_KEY = "chat:session-subscriptions:";
    private static final String ROOM_SESSION_SET_KEY = "chat:room-sessions:";

    /**
     * 채팅방 생성 후 생성자를 자동 참여시킨다.
     * 1:1 채팅(PERSONAL)인 경우 중복 체크 후 기존 방을 반환한다.
     */
    @Transactional
    public ChatRoomResponse createChatRoom(String name, ChatRoomType type, Long creatorUserId, Long targetUserId) {
        findUserOrThrow(creatorUserId);

        if (type == ChatRoomType.PERSONAL) {
            if (targetUserId == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "1:1 채팅은 대상 사용자가 필요합니다.");
            }
            ensureNotBlockedPair(creatorUserId, targetUserId);

            List<Long> existingRoomIds = chatRoomMemberRepository.findCommonPersonalRoomIds(creatorUserId, targetUserId);
            if (!existingRoomIds.isEmpty()) {
                ChatRoom existingRoom = chatRoomRepository.findById(existingRoomIds.get(0))
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
                return buildCreateRoomResponse(existingRoom, creatorUserId);
            }

            ChatRoom personalRoom = createPersonalRoom(name, creatorUserId, targetUserId, null);
            return buildCreateRoomResponse(personalRoom, creatorUserId);
        }

        ChatRoom groupRoom = createRoomWithMembers(name, ChatRoomType.GROUP, List.of(creatorUserId));
        return ChatRoomResponse.from(groupRoom);
    }

    @Transactional
    public ChatRoomResponse createOrGetPersonalRoomForConnection(Long connectionId,
                                                                 Long userAId,
                                                                 Long userBId,
                                                                 String roomName) {
        findUserOrThrow(userAId);
        findUserOrThrow(userBId);
        ensureNotBlockedPair(userAId, userBId);

        if (connectionId != null) {
            Optional<ChatRoom> byConnection = chatRoomRepository.findByConnectionId(connectionId);
            if (byConnection.isPresent()) {
                ChatRoom existingRoom = byConnection.get();
                ensurePersonalRoomMembers(existingRoom, userAId, userBId);
                return buildCreateRoomResponse(existingRoom, userAId);
            }
        }

        Long user1Id = Math.min(userAId, userBId);
        Long user2Id = Math.max(userAId, userBId);

        Optional<ChatRoom> existingByUsers = chatRoomRepository
                .findByTypeAndUser1IdAndUser2Id(ChatRoomType.PERSONAL, user1Id, user2Id);
        if (existingByUsers.isPresent()) {
            ChatRoom existingRoom = existingByUsers.get();
            existingRoom.bindConnectionIfMissing(connectionId);
            ensurePersonalRoomMembers(existingRoom, userAId, userBId);
            return buildCreateRoomResponse(existingRoom, userAId);
        }

        ChatRoom room = createPersonalRoom(roomName, userAId, userBId, connectionId);
        return buildCreateRoomResponse(room, userAId);
    }

    /**
     * 수락된 1:1 연결에 대해 PERSONAL 채팅방을 새로 만든다.
     * 단, 같은 connectionId로 이미 방이 만들어진 경우에는 중복 생성을 막기 위해 기존 방을 반환한다.
     */
    @Transactional
    public ChatRoomResponse createNewPersonalRoomForConnection(Long connectionId,
                                                               Long userAId,
                                                               Long userBId,
                                                               String roomName) {
        findUserOrThrow(userAId);
        findUserOrThrow(userBId);
        ensureNotBlockedPair(userAId, userBId);

        if (connectionId != null) {
            Optional<ChatRoom> byConnection = chatRoomRepository.findByConnectionId(connectionId);
            if (byConnection.isPresent()) {
                ChatRoom existingRoom = byConnection.get();
                ensurePersonalRoomMembers(existingRoom, userAId, userBId);
                return buildCreateRoomResponse(existingRoom, userAId);
            }
        }

        ChatRoom room = createPersonalRoom(roomName, userAId, userBId, connectionId);
        return buildCreateRoomResponse(room, userAId);
    }

    private void ensurePersonalRoomMembers(ChatRoom room, Long userAId, Long userBId) {
        if (room.getType() != ChatRoomType.PERSONAL) {
            return;
        }

        List<Long> missingUserIds = new ArrayList<>();
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(room.getId(), userAId)) {
            missingUserIds.add(userAId);
        }
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(room.getId(), userBId)) {
            missingUserIds.add(userBId);
        }

        if (missingUserIds.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = loadUsersAsMap(missingUserIds);
        Long initialLastReadMessageId = resolveLatestMessageId(room.getId());
        List<ChatRoomMember> membersToRestore = missingUserIds.stream()
                .map(userId -> ChatRoomMember.create(room, userMap.get(userId), initialLastReadMessageId))
                .collect(Collectors.toList());
        chatRoomMemberRepository.saveAll(membersToRestore);
    }

    /**
     * 매칭/임시팀방 전용 내부 유틸 메서드.
     * GROUP 채팅방을 생성하고 전달받은 멤버를 한 번에 참여시킨다.
     */
    @Transactional
    public ChatRoom createGroupRoomWithMembers(String roomName, Collection<Long> userIds) {
        return createRoomWithMembers(roomName, ChatRoomType.GROUP, userIds);
    }

    /**
     * 이미 존재하는 GROUP 채팅방에 멤버를 일괄 추가한다.
     * 중복 멤버는 자동으로 제외한다.
     */
    @Transactional
    public void addMembersToRoom(Long roomId, Collection<Long> userIds) {
        ChatRoom room = findRoomForUpdateOrThrow(roomId);

        if (room.getType() != ChatRoomType.GROUP) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "그룹 채팅방에만 멤버를 추가할 수 있습니다.");
        }

        LinkedHashSet<Long> candidateUserIds = normalizeUserIds(userIds);
        if (candidateUserIds.isEmpty()) {
            return;
        }

        Set<Long> existingUserIds = chatRoomMemberRepository.findByChatRoomId(roomId).stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        candidateUserIds.removeAll(existingUserIds);
        if (candidateUserIds.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = loadUsersAsMap(candidateUserIds);
        Long initialLastReadMessageId = resolveLatestMessageId(roomId);
        List<ChatRoomMember> newMembers = candidateUserIds.stream()
                .map(userId -> ChatRoomMember.create(room, userMap.get(userId), initialLastReadMessageId))
                .collect(Collectors.toList());

        chatRoomMemberRepository.saveAll(newMembers);
    }

    /**
     * 매칭 완료/입장/퇴장과 같은 시스템 메시지를 발행한다.
     * senderId는 반드시 해당 방의 멤버여야 한다.
     */
    @Transactional
    public ChatMessageResponse publishSystemMessage(Long roomId, Long senderId, String message, MessageType type) {
        if (type == null || type == MessageType.TEXT || type == MessageType.IMAGE || type == MessageType.TALK) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "시스템 메시지 타입은 입장 또는 퇴장이어야 합니다.");
        }

        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "메시지 내용은 비어 있을 수 없습니다.");
        }

        User sender = findUserOrThrow(senderId);
        validateRoomAccess(roomId, senderId);

        return saveAndPublishMessage(roomId, sender, message, type);
    }

    @Transactional
    public ChatMessageResponse publishEnterMessage(Long roomId, Long senderId, String message) {
        return publishSystemMessage(roomId, senderId, message, MessageType.ENTER);
    }

    @Transactional
    public ChatMessageResponse publishExitMessage(Long roomId, Long senderId, String message) {
        return publishSystemMessage(roomId, senderId, message, MessageType.EXIT);
    }

    /**
     * 채팅방 참여
     */
    @Transactional
    public void joinRoom(Long roomId, Long userId) {
        ChatRoom room = findRoomForUpdateOrThrow(roomId);

        if (room.getType() == ChatRoomType.PERSONAL) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "1:1 채팅방은 별도 참여가 불가능합니다.");
        }

        User user = findUserOrThrow(userId);

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            chatRoomMemberRepository.save(ChatRoomMember.create(room, user, resolveLatestMessageId(roomId)));
        }
    }

    /**
     * 채팅방 멤버 여부 확인
     */
    public boolean isMember(Long roomId, Long userId) {
        return chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId);
    }

    /**
     * Redis topic 구독 등록
     */
    public void enterChatRoom(String roomId) {
        topics.computeIfAbsent(roomId, key -> {
            ChannelTopic topic = new ChannelTopic(key);
            redisMessageListener.addMessageListener(redisSubscriber, topic);
            log.info("Redis Topic Registered: roomId={}", key);
            return topic;
        });
    }

    /**
     * 세션 저장
     */
    public void saveSessionInfo(String sessionId, Long userId) {
        redisTemplate.opsForValue().set(CHAT_SESSION_KEY + sessionId, String.valueOf(userId), 24, TimeUnit.HOURS);
    }

    /**
     * 사용자 탈퇴 등으로 특정 사용자의 활성 STOMP 세션 정보를 Redis에서 제거한다.
     */
    public int invalidateSessionsByUserId(Long userId) {
        if (userId == null) {
            return 0;
        }

        Set<String> sessionKeys = redisTemplate.keys(CHAT_SESSION_KEY + "*");
        if (sessionKeys == null || sessionKeys.isEmpty()) {
            return 0;
        }

        int removed = 0;
        String targetUserId = String.valueOf(userId);
        for (String sessionKey : sessionKeys) {
            Object storedUserId = redisTemplate.opsForValue().get(sessionKey);
            if (storedUserId == null || !targetUserId.equals(String.valueOf(storedUserId))) {
                continue;
            }

            String sessionId = sessionKey.substring(CHAT_SESSION_KEY.length());
            removeSessionInfo(sessionId);
            removed++;
        }

        return removed;
    }

    /**
     * 세션-채팅방 매핑 저장
     */
    public void mapSessionToRoom(String sessionId, String roomId) {
        Object previousRoomIdValue = redisTemplate.opsForValue().get(SESSION_ROOM_KEY + sessionId);
        if (previousRoomIdValue != null && !Objects.equals(String.valueOf(previousRoomIdValue), roomId)) {
            cleanupRoomTopicIfUnused(sessionId, previousRoomIdValue);
        }

        redisTemplate.opsForValue().set(SESSION_ROOM_KEY + sessionId, roomId, 24, TimeUnit.HOURS);
        addSessionToRoom(roomId, sessionId);
    }

    /**
     * STOMP subscription 단위로 session-room 매핑을 유지한다.
     * 같은 세션이 여러 채팅방을 동시에 구독해도 각 room membership을 독립적으로 추적한다.
     */
    public void registerSessionRoomSubscription(String sessionId, String subscriptionId, String roomId) {
        if (sessionId == null || roomId == null) {
            return;
        }

        if (subscriptionId == null || subscriptionId.isBlank()) {
            mapSessionToRoom(sessionId, roomId);
            return;
        }

        String subscriptionKey = SESSION_SUBSCRIPTION_KEY + sessionId;
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Object previousRoomIdValue = hashOperations.get(subscriptionKey, subscriptionId);

        hashOperations.put(subscriptionKey, subscriptionId, roomId);
        addSessionToRoom(roomId, sessionId);

        if (previousRoomIdValue != null && !Objects.equals(String.valueOf(previousRoomIdValue), roomId)) {
            List<Object> activeRooms = hashOperations.values(subscriptionKey);
            boolean stillViewingPreviousRoom = activeRooms != null && activeRooms.stream()
                    .map(String::valueOf)
                    .anyMatch(previousRoom -> Objects.equals(previousRoom, String.valueOf(previousRoomIdValue)));

            if (!stillViewingPreviousRoom) {
                cleanupRoomTopicIfUnused(sessionId, previousRoomIdValue);
            }
        }
    }

    public void unregisterSessionRoomSubscription(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }

        String subscriptionKey = SESSION_SUBSCRIPTION_KEY + sessionId;
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Object roomIdValue = hashOperations.get(subscriptionKey, subscriptionId);

        if (roomIdValue == null) {
            return;
        }

        hashOperations.delete(subscriptionKey, subscriptionId);

        List<Object> remainingRooms = hashOperations.values(subscriptionKey);
        boolean stillViewingRoom = remainingRooms != null && remainingRooms.stream()
                .map(String::valueOf)
                .anyMatch(roomId -> Objects.equals(roomId, String.valueOf(roomIdValue)));

        if (!stillViewingRoom) {
            cleanupRoomTopicIfUnused(sessionId, roomIdValue);
        }

        Long remainingSubscriptions = hashOperations.size(subscriptionKey);
        if (remainingSubscriptions != null && remainingSubscriptions == 0L) {
            redisTemplate.delete(subscriptionKey);
        }
    }

    /**
     * 사용자가 현재 특정 채팅방을 보고 있는지 Redis 세션 정보를 기준으로 확인한다.
     */
    public boolean isUserViewingRoom(Long userId, Long roomId) {
        if (userId == null || roomId == null) {
            return false;
        }

        String roomSessionSetKey = ROOM_SESSION_SET_KEY + roomId;
        try {
            Set<Object> sessionIds = redisTemplate.opsForSet().members(roomSessionSetKey);
            if (sessionIds == null || sessionIds.isEmpty()) {
                return false;
            }

            for (Object sessionIdValue : sessionIds) {
                if (sessionIdValue == null) {
                    continue;
                }

                String sessionId = String.valueOf(sessionIdValue);
                Object mappedUserId = redisTemplate.opsForValue().get(CHAT_SESSION_KEY + sessionId);
                if (mappedUserId == null) {
                    redisTemplate.opsForSet().remove(roomSessionSetKey, sessionId);
                    continue;
                }

                if (Objects.equals(String.valueOf(userId), String.valueOf(mappedUserId))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("채팅방 열람 상태 확인에 실패했습니다. userId={}, roomId={}", userId, roomId, e);
            return false;
        }
    }

    /**
     * 세션으로 사용자 조회
     */
    public Long getUserIdBySession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(CHAT_SESSION_KEY + sessionId);
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    /**
     * 세션 정보 삭제
     */
    public void removeSessionInfo(String sessionId) {
        Object roomIdValue = redisTemplate.opsForValue().get(SESSION_ROOM_KEY + sessionId);
        String subscriptionKey = SESSION_SUBSCRIPTION_KEY + sessionId;
        List<Object> subscriptionRoomIds = redisTemplate.opsForHash().values(subscriptionKey);
        redisTemplate.delete(CHAT_SESSION_KEY + sessionId);
        redisTemplate.delete(SESSION_ROOM_KEY + sessionId);
        redisTemplate.delete(subscriptionKey);

        LinkedHashSet<String> roomIdsToCleanup = new LinkedHashSet<>();
        if (roomIdValue != null) {
            roomIdsToCleanup.add(String.valueOf(roomIdValue));
        }
        if (subscriptionRoomIds != null) {
            subscriptionRoomIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .forEach(roomIdsToCleanup::add);
        }

        roomIdsToCleanup.forEach(roomId -> cleanupRoomTopicIfUnused(sessionId, roomId));
    }

    /**
     * STOMP subscribe, PATCH /read, HTTP 메시지 조회 진입이 모두 동일한 room-viewed 진입점으로 읽음 상태를 동기화한다.
     * PATCH는 websocket 구독이 늦거나 끊긴 상황에서의 fallback 역할을 맡는다.
     */
    @Transactional
    public void syncReadStateOnRoomViewed(Long roomId, Long userId) {
        ChatRoom room = findRoomForUpdateOrThrow(roomId);
        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "해당 채팅방에 접근할 수 없습니다."));

        Long previousLastReadMessageId = member.getLastReadMessageId();
        List<ChatMessage> unreadIncomingMessages = chatMessageRepository.findUnreadIncomingMessages(
                roomId,
                userId,
                previousLastReadMessageId
        );

        chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId)
                .ifPresent(lastMsg -> member.updateLastReadMessageId(lastMsg.getId()));

        if (unreadIncomingMessages.isEmpty()) {
            publishRoomListUpdate(room, userId, 0);
            return;
        }

        List<ChatRoomMember> roomMembers = loadVisibleRoomMembers(roomId);
        LocalDateTime actionTime = LocalDateTime.now(java.time.Clock.systemUTC());

        if (room.getType() == ChatRoomType.PERSONAL) {
            publishPersonalReadReceiptEvents(room, unreadIncomingMessages, roomMembers, actionTime);
            publishRoomListUpdate(room, userId, 0);
            return;
        }

        publishGroupReadReceiptEvents(room, unreadIncomingMessages, roomMembers, actionTime);
        publishRoomListUpdate(room, userId, 0);
    }

    /**
     * 기존 호출부 호환용 래퍼. 새 구현은 syncReadStateOnRoomViewed를 사용한다.
     */
    @Transactional
    public void updateLastRead(Long roomId, Long userId) {
        syncReadStateOnRoomViewed(roomId, userId);
    }

    /**
     * 발신자는 본인 메시지를 즉시 읽은 것으로 간주한다.
     */
    @Transactional
    public void markOwnMessageAsRead(Long roomId, Long userId, Long messageId) {
        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)
                .ifPresent(member -> member.updateLastReadMessageId(messageId));
    }

    /**
     * 기존 호출부 호환용 래퍼. 새 구현은 markOwnMessageAsRead를 사용한다.
     */
    @Transactional
    public void updateLastRead(Long roomId, Long userId, Long messageId) {
        markOwnMessageAsRead(roomId, userId, messageId);
    }

    /**
     * 메시지 전송 및 저장
     */
    @Transactional
    public void sendMessage(Long userId, ChatMessageRequest request) {
        MessageType messageType = normalizeMessageType(request.getMessageType());
        sendMessageInternal(userId, request.getRoomId(), request.getMessage(), messageType);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long userId, Long roomId, SendMessageRequest request) {
        MessageType messageType = normalizeMessageType(request.getMessageType());
        return sendMessageInternal(userId, roomId, request.getContent(), messageType);
    }

    @Transactional
    public ChatMessageResponse deleteMessage(Long userId, Long roomId, Long messageId) {
        validateRoomAccess(roomId, userId);

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "메시지를 찾을 수 없습니다."));

        if (!Objects.equals(message.getRoomId(), roomId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "잘못된 채팅방 메시지 요청입니다.");
        }
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 보낸 메시지만 삭제할 수 있습니다.");
        }

        ChatRoom room = findRoomOrThrow(roomId);
        message.softDelete();
        List<ChatRoomMember> roomMembers = loadVisibleRoomMembers(roomId);
        ChatMessageResponse response = ChatMessageResponse.from(
                message,
                extractProfileImage(findUserOrThrow(userId)),
                resolveMessageUnreadCount(room, message, roomMembers)
        );
        publishToRedisSilently(roomId, response);
        return response;
    }

    private ChatMessageResponse sendMessageInternal(Long userId, Long roomId, String content, MessageType messageType) {
        User user = findActiveUserOrThrow(userId);
        validateRoomAccess(roomId, userId);

        validateMessagePayload(content, messageType);
        validateNotBlocked(roomId, userId);

        return saveAndPublishMessage(roomId, user, content, messageType);
    }

    private ChatMessageResponse saveAndPublishMessage(Long roomId, User sender, String content, MessageType messageType) {
        ChatRoom room = findRoomForUpdateOrThrow(roomId);

        ChatMessage chatMessage = ChatMessage.create(roomId, sender.getId(), sender.getNickname(), content, messageType);
        chatMessageRepository.save(chatMessage);

        room.updateLastMessage(summarizeForRoomList(content, messageType), chatMessage.getCreatedAt());

        markOwnMessageAsRead(roomId, sender.getId(), chatMessage.getId());
        List<ChatRoomMember> roomMembers = loadVisibleRoomMembers(roomId);
        applyImmediateReadForViewingMembers(room, sender.getId(), chatMessage, roomMembers);

        ChatMessageResponse response = ChatMessageResponse.from(
                chatMessage,
                extractProfileImage(sender),
                resolveMessageUnreadCount(room, chatMessage, roomMembers)
        );
        publishToRedisSilently(roomId, response);
        publishRoomListUpdates(room, roomMembers.stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet()));
        publishChatMessageNotifications(roomId, sender, chatMessage);
        return response;
    }

    /**
     * 채팅방 나가기 (참여 해제)
     */
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        ChatRoom room = findRoomForUpdateOrThrow(roomId);
        User user = findUserOrThrow(userId);

        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "해당 채팅방의 멤버가 아닙니다."));

        ChatMessage exitMessage = ChatMessage.create(
                roomId,
                userId,
                user.getNickname(),
                user.getNickname() + "님이 퇴장하셨습니다.",
                MessageType.EXIT
        );
        chatMessageRepository.save(exitMessage);
        List<ChatRoomMember> roomMembers = loadVisibleRoomMembers(roomId);

        ChatMessageResponse response = ChatMessageResponse.from(
                exitMessage,
                extractProfileImage(user),
                resolveMessageUnreadCount(room, exitMessage, roomMembers)
        );
        publishToRedisSilently(roomId, response);

        chatRoomMemberRepository.delete(member);
    }

    /**
     * 내 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> findAllRooms(Long userId) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByUser_IdWithRoom(userId);

        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roomIds = members.stream()
                .map(m -> m.getChatRoom().getId())
                .collect(Collectors.toList());

        Map<Long, ChatMessage> latestMessageMap = chatMessageRepository.findLatestMessagesByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(ChatMessage::getRoomId, m -> m));

        Map<Long, Integer> unreadCountMap = chatMessageRepository.countUnreadByUserId(userId).stream()
                .collect(Collectors.toMap(
                        obj -> (Long) obj[0],
                        obj -> ((Long) obj[1]).intValue()
                ));

        Map<Long, User> counterpartByRoomId = buildCounterpartMap(roomIds, userId);

        return members.stream()
                .map(member -> {
                    ChatRoom room = member.getChatRoom();
                    ChatMessage latestMsg = latestMessageMap.get(room.getId());

                    String content = (room.getLastMessage() != null && !room.getLastMessage().isBlank())
                            ? room.getLastMessage()
                            : (latestMsg != null ? latestMsg.getDisplayContent() : null);
                    LocalDateTime time = room.getLastMessageAt() != null
                            ? room.getLastMessageAt()
                            : (latestMsg != null ? latestMsg.getCreatedAt() : null);
                    int unreadCount = unreadCountMap.getOrDefault(room.getId(), 0);

                    User targetUser = null;
                    if (room.getType() == ChatRoomType.PERSONAL) {
                        targetUser = counterpartByRoomId.get(room.getId());
                    }

                    Long targetUserId = targetUser != null ? targetUser.getId() : null;
                    String targetNickname = targetUser != null ? targetUser.getNickname() : null;
                    String targetProfileImage = extractProfileImage(targetUser);
                    ChatParticipantDetailResponse targetProfile = targetUser != null
                            ? toParticipantDetailResponse(targetUser)
                            : null;
                    String displayName = room.getName();
                    if (room.getType() == ChatRoomType.PERSONAL && targetNickname != null && !targetNickname.isBlank()) {
                        displayName = targetNickname;
                    }

                    return ChatRoomResponse.from(room, displayName, content, time, unreadCount,
                            targetUserId, targetNickname,
                            targetUser != null ? targetUser.getStudentNum() : null,
                            targetProfileImage, targetProfile);
                })
                .sorted((r1, r2) -> {
                    java.time.OffsetDateTime t1 = (r1.getLatestMessageTime() != null) ? r1.getLatestMessageTime() : r1.getCreatedAt();
                    java.time.OffsetDateTime t2 = (r2.getLatestMessageTime() != null) ? r2.getLatestMessageTime() : r2.getCreatedAt();
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());
    }

    /**
     * 1:1 채팅방 상대 프로필 조회
     */
    @Transactional(readOnly = true)
    public ChatParticipantDetailResponse getCounterpartProfile(Long roomId, Long userId) {
        validateRoomAccess(roomId, userId);

        User counterpart = chatRoomMemberRepository.findByChatRoomIdInWithUser(List.of(roomId)).stream()
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .filter(memberUser -> !Objects.equals(memberUser.getId(), userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "상대 사용자 정보를 찾을 수 없습니다."));

        return toParticipantDetailResponse(counterpart);
    }

    @Transactional(readOnly = true)
    public ChatParticipantProfileResponse getParticipantProfile(Long roomId, Long userId, Long targetUserId) {
        validateRoomAccess(roomId, userId);

        if (Objects.equals(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인 프로필은 이 API로 조회할 수 없습니다.");
        }

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "선택한 사용자가 해당 채팅방 참여자가 아닙니다.");
        }

        User participant = userRepository.findAllByIdWithProfile(List.of(targetUserId)).stream()
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        return toParticipantProfileResponse(participant);
    }

    /**
     * 특정 채팅방 메시지 조회
     */
    @Transactional
    public List<ChatMessageResponse> findMessagesByRoomId(Long roomId, Long userId, Long lastMessageId, int size) {
        validateRoomAccess(roomId, userId);
        ChatRoom room = findRoomOrThrow(roomId);

        Pageable pageable = PageRequest.of(0, size);
        List<ChatMessage> messages = chatMessageRepository.findMessagesCursor(roomId, lastMessageId, pageable);

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        syncReadStateOnRoomViewed(roomId, userId);
        List<ChatRoomMember> roomMembers = loadVisibleRoomMembers(roomId);

        Set<Long> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findAllByIdWithProfile(senderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<ChatMessageResponse> response = messages.stream()
                .map(msg -> {
                    User sender = userMap.get(msg.getSenderId());
                    String profileImage = (sender != null && sender.getUserProfile() != null)
                            ? sender.getUserProfile().getProfileImagePath()
                            : null;
                    return ChatMessageResponse.from(msg, profileImage, resolveMessageUnreadCount(room, msg, roomMembers));
                })
                .collect(Collectors.toList());

        Collections.reverse(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatParticipantDetailResponse> getParticipantProfiles(Long roomId, Long userId) {
        validateRoomAccess(roomId, userId);

        return chatRoomMemberRepository.findByChatRoomIdInWithUser(List.of(roomId)).stream()
                .filter(member -> !member.isHidden())
                .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt))
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .map(this::toParticipantDetailResponse)
                .toList();
    }

    private Map<Long, User> buildCounterpartMap(List<Long> roomIds, Long myUserId) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ChatRoomMember> allMembers = chatRoomMemberRepository.findByChatRoomIdInWithUser(roomIds);
        Map<Long, User> result = new HashMap<>();

        for (ChatRoomMember member : allMembers) {
            Long roomId = member.getChatRoom().getId();
            User user = member.getUser();
            if (Objects.equals(user.getId(), myUserId)) {
                continue;
            }
            result.putIfAbsent(roomId, user);
        }
        return result;
    }

    private ChatRoomResponse buildCreateRoomResponse(ChatRoom room, Long requesterUserId) {
        if (room.getType() != ChatRoomType.PERSONAL) {
            return ChatRoomResponse.from(room);
        }

        Long counterpartUserId = resolveCounterpartUserId(room, requesterUserId);
        if (counterpartUserId == null) {
            return ChatRoomResponse.from(room);
        }

        User counterpart = findUserWithProfileOrThrow(counterpartUserId);
        String targetNickname = counterpart.getNickname();
        String displayName = (targetNickname != null && !targetNickname.isBlank())
                ? targetNickname
                : room.getName();

        return ChatRoomResponse.from(
                room,
                displayName,
                room.getLastMessage(),
                room.getLastMessageAt(),
                0,
                counterpart.getId(),
                targetNickname,
                counterpart.getStudentNum(),
                extractProfileImage(counterpart),
                toParticipantDetailResponse(counterpart)
        );
    }

    private Long resolveCounterpartUserId(ChatRoom room, Long requesterUserId) {
        if (room.getType() != ChatRoomType.PERSONAL) {
            return null;
        }

        if (Objects.equals(requesterUserId, room.getUser1Id())) {
            return room.getUser2Id();
        }
        if (Objects.equals(requesterUserId, room.getUser2Id())) {
            return room.getUser1Id();
        }

        return chatRoomMemberRepository.findByChatRoomIdInWithUser(List.of(room.getId())).stream()
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(memberUserId -> !Objects.equals(memberUserId, requesterUserId))
                .findFirst()
                .orElse(null);
    }

    private ChatRoom createPersonalRoom(String roomName, Long creatorUserId, Long targetUserId, Long connectionId) {
        if (roomName == null || roomName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "채팅방 이름은 비어 있을 수 없습니다.");
        }

        Map<Long, User> userMap = loadUsersAsMap(List.of(creatorUserId, targetUserId));
        ChatRoom chatRoom = ChatRoom.createPersonal(roomName, creatorUserId, targetUserId, connectionId);
        chatRoomRepository.save(chatRoom);

        List<ChatRoomMember> members = List.of(
                ChatRoomMember.create(chatRoom, userMap.get(creatorUserId)),
                ChatRoomMember.create(chatRoom, userMap.get(targetUserId))
        );
        chatRoomMemberRepository.saveAll(members);
        return chatRoom;
    }

    private ChatRoom createRoomWithMembers(String roomName, ChatRoomType roomType, Collection<Long> userIds) {
        if (roomName == null || roomName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "채팅방 이름은 비어 있을 수 없습니다.");
        }

        LinkedHashSet<Long> uniqueUserIds = normalizeUserIds(userIds);
        if (uniqueUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "채팅방 멤버는 최소 1명 이상이어야 합니다.");
        }

        if (roomType == ChatRoomType.PERSONAL && uniqueUserIds.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "1:1 채팅방은 정확히 2명이 필요합니다.");
        }

        if (roomType == ChatRoomType.PERSONAL) {
            List<Long> ids = new ArrayList<>(uniqueUserIds);
            return createPersonalRoom(roomName, ids.get(0), ids.get(1), null);
        }

        Map<Long, User> userMap = loadUsersAsMap(uniqueUserIds);

        ChatRoom chatRoom = ChatRoom.create(roomName, roomType);
        chatRoomRepository.save(chatRoom);

        List<ChatRoomMember> members = uniqueUserIds.stream()
                .map(userId -> ChatRoomMember.create(chatRoom, userMap.get(userId)))
                .collect(Collectors.toList());
        chatRoomMemberRepository.saveAll(members);

        return chatRoom;
    }

    private Map<Long, User> loadUsersAsMap(Collection<Long> userIds) {
        List<User> users = userRepository.findAllById(userIds);
        if (users.size() != new HashSet<>(userIds).size()) {
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }

        return users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private LinkedHashSet<Long> normalizeUserIds(Collection<Long> userIds) {
        if (userIds == null) {
            return new LinkedHashSet<>();
        }

        return userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private User findUserWithProfileOrThrow(Long userId) {
        return userRepository.findAllByIdWithProfile(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private User findActiveUserOrThrow(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthException(AuthErrorCode.USER_DELETED);
        }
        return user;
    }

    private ChatRoom findRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "채팅방을 찾을 수 없습니다."));
    }

    private ChatRoom findRoomForUpdateOrThrow(Long roomId) {
        Optional<ChatRoom> lockedRoom = chatRoomRepository.findByIdForUpdate(roomId);
        if (lockedRoom != null && lockedRoom.isPresent()) {
            return lockedRoom.get();
        }
        return findRoomOrThrow(roomId);
    }

    private String extractProfileImage(User user) {
        if (user == null) {
            return null;
        }
        return (user.getUserProfile() != null)
                ? user.getUserProfile().getProfileImagePath()
                : null;
    }

    private void publishToRedis(Long roomId, ChatMessageResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.convertAndSend(roomId.toString(), payload);
            log.info("Message Published: roomId={}", roomId);
        } catch (JsonProcessingException e) {
            log.error("Redis publish serialization failed. roomId={}", roomId, e);
            throw new RuntimeException("채팅 메시지 직렬화에 실패했습니다.");
        }
    }

    private void publishToRedisSilently(Long roomId, ChatMessageResponse response) {
        try {
            publishToRedis(roomId, response);
        } catch (RuntimeException e) {
            log.error("Redis publish skipped after DB save. roomId={}", roomId, e);
        }
    }

    private void publishRoomListUpdates(ChatRoom room, Collection<Long> userIds) {
        if (room == null || userIds == null || userIds.isEmpty()) {
            return;
        }

        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(userId -> publishRoomListUpdate(room, userId, resolveRoomListUnreadCount(userId, room.getId())));
    }

    private void publishRoomListUpdate(ChatRoom room, Long userId, int unreadCount) {
        if (room == null || userId == null) {
            return;
        }

        try {
            ChatRoomListUpdateResponse payload = ChatRoomListUpdateResponse.of(
                    userId,
                    room.getId(),
                    room.getLastMessage(),
                    room.getLastMessageAt(),
                    unreadCount
            );
            messagingTemplate.convertAndSend("/sub/chat/list/" + userId, payload);
        } catch (RuntimeException e) {
            log.error("Room list update publish failed. roomId={}, userId={}", room.getId(), userId, e);
        }
    }

    private int resolveRoomListUnreadCount(Long userId, Long roomId) {
        List<Object[]> unreadCounts = chatMessageRepository.countUnreadByUserId(userId);
        if (unreadCounts == null || unreadCounts.isEmpty()) {
            return 0;
        }

        return unreadCounts.stream()
                .filter(row -> row != null && row.length >= 2 && Objects.equals(row[0], roomId))
                .findFirst()
                .map(row -> ((Long) row[1]).intValue())
                .orElse(0);
    }

    private void cleanupRoomTopicIfUnused(String sessionId, Object roomIdValue) {
        if (roomIdValue == null) {
            return;
        }

        String roomId = String.valueOf(roomIdValue);
        String roomSessionSetKey = ROOM_SESSION_SET_KEY + roomId;
        redisTemplate.opsForSet().remove(roomSessionSetKey, sessionId);

        Long sessionCount = redisTemplate.opsForSet().size(roomSessionSetKey);
        if (sessionCount != null && sessionCount == 0L) {
            redisTemplate.delete(roomSessionSetKey);
            removeTopic(roomId);
        }
    }

    private void addSessionToRoom(String roomId, String sessionId) {
        redisTemplate.opsForSet().add(ROOM_SESSION_SET_KEY + roomId, sessionId);
    }

    private void publishChatMessageNotifications(Long roomId, User sender, ChatMessage chatMessage) {
        if (!isPushEligibleMessage(chatMessage.getType())) {
            return;
        }

        List<ChatRoomMember> members = loadVisibleRoomMembers(roomId);
        if (members.isEmpty()) {
            return;
        }

        String senderNickname = sender.getNickname() != null && !sender.getNickname().isBlank()
                ? sender.getNickname()
                : "상대방";
        String preview = summarizeChatPushPreview(chatMessage);
        String deeplink = "airconnect://chat/rooms/" + roomId;

        for (ChatRoomMember member : members) {
            Long recipientUserId = member.getUser().getId();
            if (Objects.equals(recipientUserId, sender.getId())) {
                continue;
            }
            if (isUserViewingRoom(recipientUserId, roomId)) {
                log.debug("같은 채팅방을 보고 있어 OS push를 생략합니다. roomId={}, recipientUserId={}",
                        roomId, recipientUserId);
                continue;
            }

            try {
                var payload = objectMapper.createObjectNode();
                payload.put("chatRoomId", roomId);
                payload.put("messageId", chatMessage.getId());
                payload.put("senderUserId", sender.getId());
                payload.put("senderNickname", senderNickname);
                payload.put("messagePreview", preview);
                payload.put("messageType", chatMessage.getType().name());

                notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                        recipientUserId,
                        NotificationType.CHAT_MESSAGE_RECEIVED,
                        senderNickname,
                        preview,
                        deeplink,
                        sender.getId(),
                        extractProfileImage(sender),
                        payload.toString(),
                        "chat-message:" + chatMessage.getId() + ":received"
                ));
            } catch (Exception e) {
                log.error("채팅 메시지 알림 저장에 실패했습니다. roomId={}, messageId={}, recipientUserId={}",
                        roomId, chatMessage.getId(), recipientUserId, e);
            }
        }
    }

    private void removeTopic(String roomId) {
        ChannelTopic topic = topics.remove(roomId);
        if (topic == null) {
            return;
        }
        redisMessageListener.removeMessageListener(redisSubscriber, topic);
        log.info("Redis Topic Unregistered: roomId={}", roomId);
    }

    /**
     * 채팅방 접근 권한 확인
     */
    private void validateRoomAccess(Long roomId, Long userId) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 채팅방에 접근할 수 없습니다.");
        }
    }

    private MessageType normalizeMessageType(MessageType messageType) {
        if (messageType == null || messageType == MessageType.TALK) {
            return MessageType.TEXT;
        }
        return messageType;
    }

    private boolean isPushEligibleMessage(MessageType messageType) {
        return messageType == MessageType.TEXT || messageType == MessageType.IMAGE;
    }

    private void validateMessagePayload(String content, MessageType messageType) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "메시지 내용은 비어 있을 수 없습니다.");
        }
        if (messageType == MessageType.TEXT && content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "텍스트 메시지는 비어 있을 수 없습니다.");
        }
    }

    private String summarizeForRoomList(String content, MessageType messageType) {
        if (messageType == MessageType.IMAGE) {
            return "[이미지]";
        }
        String normalized = content == null ? "" : content.trim();
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private String summarizeChatPushPreview(ChatMessage chatMessage) {
        if (chatMessage.getType() == MessageType.IMAGE) {
            return "[이미지]";
        }

        String content = chatMessage.getDisplayContent();
        if (content == null || content.isBlank()) {
            return "새 메시지가 도착했어요.";
        }

        String normalized = content.trim();
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private List<ChatRoomMember> loadVisibleRoomMembers(Long roomId) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomIdAndHiddenAtIsNullOrderByJoinedAtAsc(roomId);
        return members != null ? members : Collections.emptyList();
    }

    private void applyImmediateReadForViewingMembers(ChatRoom room,
                                                     Long senderId,
                                                     ChatMessage chatMessage,
                                                     List<ChatRoomMember> roomMembers) {
        if (chatMessage == null || roomMembers == null || roomMembers.isEmpty()) {
            return;
        }

        if (room.getType() == ChatRoomType.PERSONAL) {
            applyImmediateReadForPersonalRoom(room, senderId, chatMessage, roomMembers);
            return;
        }

        applyImmediateReadForGroupRoom(room, senderId, chatMessage, roomMembers);
    }

    private void applyImmediateReadForPersonalRoom(ChatRoom room,
                                                   Long senderId,
                                                   ChatMessage message,
                                                   List<ChatRoomMember> roomMembers) {
        roomMembers.stream()
                .filter(member -> isUnreadRecipient(member, message))
                .filter(member -> isUserViewingRoom(member.getUser().getId(), room.getId()))
                .findFirst()
                .ifPresent(member -> member.updateLastReadMessageId(message.getId()));

        markMessageAsFullyReadIfNeeded(room, message, roomMembers, LocalDateTime.now(java.time.Clock.systemUTC()));
    }

    private void applyImmediateReadForGroupRoom(ChatRoom room,
                                                Long senderId,
                                                ChatMessage message,
                                                List<ChatRoomMember> roomMembers) {
        for (ChatRoomMember member : roomMembers) {
            Long memberUserId = member.getUser().getId();
            if (Objects.equals(memberUserId, senderId)) {
                continue;
            }
            if (!isUnreadRecipient(member, message)) {
                continue;
            }
            if (!isUserViewingRoom(memberUserId, room.getId())) {
                continue;
            }

            member.updateLastReadMessageId(message.getId());
        }

        markMessageAsFullyReadIfNeeded(room, message, roomMembers, LocalDateTime.now(java.time.Clock.systemUTC()));
    }

    private Integer resolveMessageUnreadCount(ChatRoom room, ChatMessage message, List<ChatRoomMember> roomMembers) {
        if (message == null || !message.isUnreadTrackable()) {
            return 0;
        }
        if (roomMembers == null || roomMembers.isEmpty()) {
            return 0;
        }

        if (room.getType() == ChatRoomType.PERSONAL) {
            return resolvePersonalUnreadCount(message, roomMembers);
        }

        return resolveGroupUnreadCount(message, roomMembers);
    }

    private Integer resolvePersonalUnreadCount(ChatMessage message, List<ChatRoomMember> roomMembers) {
        long unreadCount = roomMembers.stream()
                .filter(member -> isUnreadRecipient(member, message))
                .filter(member -> !member.hasRead(message.getId()))
                .count();

        return unreadCount > 0 ? 1 : 0;
    }

    private Integer resolveGroupUnreadCount(ChatMessage message, List<ChatRoomMember> roomMembers) {
        long unreadCount = roomMembers.stream()
                .filter(member -> isUnreadRecipient(member, message))
                .filter(member -> !member.hasRead(message.getId()))
                .count();

        return Math.toIntExact(unreadCount);
    }

    private void publishPersonalReadReceiptEvents(ChatRoom room,
                                                  List<ChatMessage> messages,
                                                  List<ChatRoomMember> roomMembers,
                                                  LocalDateTime actionTime) {
        publishReadReceiptEvents(room, messages, roomMembers, actionTime);
    }

    private void publishGroupReadReceiptEvents(ChatRoom room,
                                               List<ChatMessage> messages,
                                               List<ChatRoomMember> roomMembers,
                                               LocalDateTime actionTime) {
        publishReadReceiptEvents(room, messages, roomMembers, actionTime);
    }

    private void publishReadReceiptEvents(ChatRoom room,
                                          List<ChatMessage> messages,
                                          List<ChatRoomMember> roomMembers,
                                          LocalDateTime actionTime) {
        for (ChatMessage message : messages) {
            if (!message.isUnreadTrackable()) {
                continue;
            }

            markMessageAsFullyReadIfNeeded(room, message, roomMembers, actionTime);
            ChatMessageResponse readReceipt = ChatMessageResponse.readReceipt(
                    room.getId(),
                    message.getId(),
                    actionTime,
                    resolveMessageUnreadCount(room, message, roomMembers)
            );
            publishToRedisSilently(room.getId(), readReceipt);
        }
    }

    private void markMessageAsFullyReadIfNeeded(ChatRoom room,
                                                ChatMessage message,
                                                List<ChatRoomMember> roomMembers,
                                                LocalDateTime readAt) {
        if (!message.isUnreadTrackable()) {
            return;
        }
        if (resolveMessageUnreadCount(room, message, roomMembers) == 0) {
            message.markRead(readAt);
        }
    }

    private boolean isUnreadRecipient(ChatRoomMember member, ChatMessage message) {
        if (member == null || member.isHidden() || message == null) {
            return false;
        }
        if (Objects.equals(member.getUser().getId(), message.getSenderId())) {
            return false;
        }
        if (member.getLastReadMessageId() != null) {
            return true;
        }
        LocalDateTime joinedAt = member.getJoinedAt();
        return joinedAt == null || !joinedAt.isAfter(message.getCreatedAt());
    }

    private Long resolveLatestMessageId(Long roomId) {
        return chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId)
                .map(ChatMessage::getId)
                .orElse(null);
    }

    private ChatParticipantProfileResponse toParticipantProfileResponse(User user) {
        UserProfile profile = user.getUserProfile();
        boolean profileImageUploaded = profile != null
                && profile.getProfileImagePath() != null
                && !profile.getProfileImagePath().isBlank();

        return ChatParticipantProfileResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .deptName(user.getDeptName())
                .profileImage(profile != null ? profile.getProfileImagePath() : null)
                .gender(profile != null ? profile.getGender() : null)
                .age(profile != null ? profile.getAge() : null)
                .profileExists(profile != null)
                .profileImageUploaded(profileImageUploaded)
                .build();
    }

    private ChatParticipantDetailResponse toParticipantDetailResponse(User user) {
        UserProfile profile = user.getUserProfile();
        boolean profileImageUploaded = profile != null
                && profile.getProfileImagePath() != null
                && !profile.getProfileImagePath().isBlank();

        UserProfileResponse profileResponse = profile != null
                ? UserProfileResponse.from(profile, imageUrlBase)
                : null;

        return ChatParticipantDetailResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .deptName(user.getDeptName())
                .studentNum(user.getStudentNum())
                .profileImage(profile != null ? profile.getProfileImagePath() : null)
                .gender(profile != null ? profile.getGender() : null)
                .age(profile != null ? profile.getAge() : null)
                .profileExists(profile != null)
                .profileImageUploaded(profileImageUploaded)
                .profile(profileResponse)
                .build();
    }

    private void validateNotBlocked(Long roomId, Long userId) {
        List<Long> participantUserIds = chatRoomMemberRepository.findUserIdsByChatRoomId(roomId);
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return;
        }

        List<Long> counterpartIds = participantUserIds.stream()
                .filter(participantUserId -> !Objects.equals(participantUserId, userId))
                .toList();
        if (counterpartIds.isEmpty()) {
            return;
        }

        if (userBlockPolicyService.findAnyBlockedCounterpart(userId, counterpartIds).isPresent()) {
            throw new BusinessException(ErrorCode.USER_BLOCKED_INTERACTION, "차단 관계에서는 메시지를 보낼 수 없습니다.");
        }
    }

    private void ensureNotBlockedPair(Long userAId, Long userBId) {
        if (userBlockPolicyService.hasBlockRelation(userAId, userBId)) {
            throw new BusinessException(ErrorCode.USER_BLOCKED_INTERACTION, "차단 관계에서는 1:1 채팅방을 생성할 수 없습니다.");
        }
    }
}
