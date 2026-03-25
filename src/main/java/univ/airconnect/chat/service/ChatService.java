package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.user.domain.entity.User;
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
    private final ObjectMapper objectMapper;

    private final Map<String, ChannelTopic> topics = new ConcurrentHashMap<>();

    private static final String CHAT_SESSION_KEY = "chat:session:";
    private static final String SESSION_ROOM_KEY = "chat:session-room:";
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

            List<Long> existingRoomIds = chatRoomMemberRepository.findCommonPersonalRoomIds(creatorUserId, targetUserId);
            if (!existingRoomIds.isEmpty()) {
                ChatRoom existingRoom = chatRoomRepository.findById(existingRoomIds.get(0))
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
                return ChatRoomResponse.from(existingRoom);
            }

            ChatRoom personalRoom = createPersonalRoom(name, creatorUserId, targetUserId, null);
            return ChatRoomResponse.from(personalRoom);
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

        if (connectionId != null) {
            Optional<ChatRoom> byConnection = chatRoomRepository.findByConnectionId(connectionId);
            if (byConnection.isPresent()) {
                ChatRoom existingRoom = byConnection.get();
                ensurePersonalRoomMembers(existingRoom, userAId, userBId);
                return ChatRoomResponse.from(existingRoom);
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
            return ChatRoomResponse.from(existingRoom);
        }

        ChatRoom room = createPersonalRoom(roomName, userAId, userBId, connectionId);
        return ChatRoomResponse.from(room);
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
        List<ChatRoomMember> membersToRestore = missingUserIds.stream()
                .map(userId -> ChatRoomMember.create(room, userMap.get(userId)))
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
        ChatRoom room = findRoomOrThrow(roomId);

        if (room.getType() != ChatRoomType.GROUP) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "GROUP 채팅방에만 멤버를 추가할 수 있습니다.");
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
        List<ChatRoomMember> newMembers = candidateUserIds.stream()
                .map(userId -> ChatRoomMember.create(room, userMap.get(userId)))
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "시스템 메시지 타입은 ENTER 또는 EXIT 이어야 합니다.");
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
        ChatRoom room = findRoomOrThrow(roomId);

        if (room.getType() == ChatRoomType.PERSONAL) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "1:1 채팅방은 별도 참여가 불가능합니다.");
        }

        User user = findUserOrThrow(userId);

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            chatRoomMemberRepository.save(ChatRoomMember.create(room, user));
        }
    }

    /**
     * 채팅방 멤버 여부 확인
     */
    public boolean isMember(Long roomId, Long userId) {
        return chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId);
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
     * 세션-채팅방 매핑 저장
     */
    public void mapSessionToRoom(String sessionId, String roomId) {
        redisTemplate.opsForValue().set(SESSION_ROOM_KEY + sessionId, roomId, 24, TimeUnit.HOURS);
        redisTemplate.opsForSet().add(ROOM_SESSION_SET_KEY + roomId, sessionId);
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
        redisTemplate.delete(CHAT_SESSION_KEY + sessionId);
        redisTemplate.delete(SESSION_ROOM_KEY + sessionId);
        cleanupRoomTopicIfUnused(sessionId, roomIdValue);
    }

    /**
     * 특정 채팅방의 읽음 상태 업데이트 (채팅방 진입/조회용)
     */
    @Transactional
    public void updateLastRead(Long roomId, Long userId) {
        chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .ifPresent(member -> chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId)
                        .ifPresent(lastMsg -> member.updateLastReadMessageId(lastMsg.getId())));
    }

    /**
     * 특정 메시지 ID로 마지막 읽음 상태 업데이트 (메시지 전송 직후용)
     */
    @Transactional
    public void updateLastRead(Long roomId, Long userId, Long messageId) {
        chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .ifPresent(member -> member.updateLastReadMessageId(messageId));
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

        message.softDelete();
        ChatMessageResponse response = ChatMessageResponse.from(message, extractProfileImage(findUserOrThrow(userId)));
        publishToRedisSilently(roomId, response);
        return response;
    }

    private ChatMessageResponse sendMessageInternal(Long userId, Long roomId, String content, MessageType messageType) {
        User user = findUserOrThrow(userId);
        validateRoomAccess(roomId, userId);

        validateMessagePayload(content, messageType);
        validateNotBlocked(roomId, userId);

        return saveAndPublishMessage(roomId, user, content, messageType);
    }

    private ChatMessageResponse saveAndPublishMessage(Long roomId, User sender, String content, MessageType messageType) {
        ChatMessage chatMessage = ChatMessage.create(roomId, sender.getId(), sender.getNickname(), content, messageType);
        chatMessageRepository.save(chatMessage);

        findRoomOrThrow(roomId).updateLastMessage(summarizeForRoomList(content, messageType), chatMessage.getCreatedAt());

        updateLastRead(roomId, sender.getId(), chatMessage.getId());

        ChatMessageResponse response = ChatMessageResponse.from(chatMessage, extractProfileImage(sender));
        publishToRedisSilently(roomId, response);
        return response;
    }

    /**
     * 채팅방 나가기 (참여 해제)
     */
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
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

        ChatMessageResponse response = ChatMessageResponse.from(exitMessage, extractProfileImage(user));
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
                    String displayName = room.getName();
                    if (room.getType() == ChatRoomType.PERSONAL && targetNickname != null && !targetNickname.isBlank()) {
                        displayName = targetNickname;
                    }

                    return ChatRoomResponse.from(room, displayName, content, time, unreadCount,
                            targetUserId, targetNickname, targetProfileImage);
                })
                .sorted((r1, r2) -> {
                    LocalDateTime t1 = (r1.getLatestMessageTime() != null) ? r1.getLatestMessageTime() : r1.getCreatedAt();
                    LocalDateTime t2 = (r2.getLatestMessageTime() != null) ? r2.getLatestMessageTime() : r2.getCreatedAt();
                    return t2.compareTo(t1);
                })
                .collect(Collectors.toList());
    }

    /**
     * 특정 채팅방 메시지 조회
     */
    @Transactional
    public List<ChatMessageResponse> findMessagesByRoomId(Long roomId, Long userId, Long lastMessageId, int size) {
        validateRoomAccess(roomId, userId);

        Pageable pageable = PageRequest.of(0, size);
        List<ChatMessage> messages = chatMessageRepository.findMessagesCursor(roomId, lastMessageId, pageable);

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

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
                    return ChatMessageResponse.from(msg, profileImage);
                })
                .collect(Collectors.toList());

        markIncomingMessagesRead(roomId, userId);
        updateLastRead(roomId, userId);

        Collections.reverse(response);
        return response;
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

    private ChatRoom findRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "채팅방을 찾을 수 없습니다."));
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
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 채팅방에 접근할 수 없습니다.");
        }
    }

    private MessageType normalizeMessageType(MessageType messageType) {
        if (messageType == null || messageType == MessageType.TALK) {
            return MessageType.TEXT;
        }
        return messageType;
    }

    private void validateMessagePayload(String content, MessageType messageType) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "메시지 내용은 비어 있을 수 없습니다.");
        }
        if (messageType == MessageType.TEXT && content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "TEXT 메시지는 비어 있을 수 없습니다.");
        }
    }

    private String summarizeForRoomList(String content, MessageType messageType) {
        if (messageType == MessageType.IMAGE) {
            return "[이미지]";
        }
        String normalized = content == null ? "" : content.trim();
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private void markIncomingMessagesRead(Long roomId, Long userId) {
        chatMessageRepository.markIncomingMessagesRead(roomId, userId, LocalDateTime.now());
    }

    private void validateNotBlocked(Long roomId, Long userId) {
        // TODO: 차단 엔티티/테이블 도입 시 room 참여자 간 block 관계 검증을 연결한다.
    }
}
