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

    /**
     * 채팅방 생성 후 생성자를 자동 참여시킨다.
     * 1:1 채팅(PERSONAL)인 경우 중복 체크 후 기존 방을 반환한다.
     */
    @Transactional
    public ChatRoomResponse createChatRoom(String name, ChatRoomType type, Long creatorUserId, Long targetUserId) {
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (type == ChatRoomType.PERSONAL) {
            if (targetUserId == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }

            List<Long> existingRoomIds = chatRoomMemberRepository.findCommonPersonalRoomIds(creatorUserId, targetUserId);
            if (!existingRoomIds.isEmpty()) {
                ChatRoom existingRoom = chatRoomRepository.findById(existingRoomIds.get(0))
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
                return ChatRoomResponse.from(existingRoom);
            }
        }

        ChatRoom chatRoom = ChatRoom.create(name, type);
        chatRoomRepository.save(chatRoom);

        chatRoomMemberRepository.save(ChatRoomMember.create(chatRoom, creator));

        if (type == ChatRoomType.PERSONAL && targetUserId != null) {
            User target = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
            chatRoomMemberRepository.save(ChatRoomMember.create(chatRoom, target));
        }

        return ChatRoomResponse.from(chatRoom);
    }

    /**
     * 채팅방 참여
     */
    @Transactional
    public void joinRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        if (room.getType() == ChatRoomType.PERSONAL) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

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
        redisTemplate.delete(CHAT_SESSION_KEY + sessionId);
        redisTemplate.delete(SESSION_ROOM_KEY + sessionId);
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
     * 특정 메시지의 안읽은 사람 수 계산 (최적화용)
     */
    private int getUnreadCount(Long messageId, List<ChatRoomMember> members) {
        return (int) members.stream()
                .filter(m -> m.getLastReadMessageId() == null || m.getLastReadMessageId() < messageId)
                .count();
    }

    /**
     * 특정 메시지의 안읽은 사람 수 계산 (단일 조회용)
     */
    public int getUnreadCount(Long roomId, Long messageId) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomId(roomId);
        return getUnreadCount(messageId, members);
    }

    /**
     * 메시지 전송 및 저장
     */
    @Transactional
    public void sendMessage(Long userId, ChatMessageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        validateRoomAccess(request.getRoomId(), userId);

        ChatMessage chatMessage = ChatMessage.create(
                request.getRoomId(),
                userId,
                user.getNickname(),
                request.getMessage(),
                MessageType.TALK
        );
        chatMessageRepository.save(chatMessage);

        // 보낸 사람의 마지막 읽은 메시지 즉시 반영 (재조회하지 않음)
        updateLastRead(request.getRoomId(), userId, chatMessage.getId());

        String profileImage = (user.getUserProfile() != null)
                ? user.getUserProfile().getProfileImageKey()
                : null;

        int unreadCount = getUnreadCount(request.getRoomId(), chatMessage.getId());
        ChatMessageResponse response = ChatMessageResponse.from(chatMessage, profileImage, unreadCount);

        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.convertAndSend(request.getRoomId().toString(), payload);
            log.info("Message Published: roomId={}, senderId={}", request.getRoomId(), userId);
        } catch (JsonProcessingException e) {
            log.error("Redis publish serialization failed. roomId={}, senderId={}", request.getRoomId(), userId, e);
            throw new RuntimeException("채팅 메시지 직렬화에 실패했습니다.");
        }
    }

    /**
     * 채팅방 나가기 (참여 해제)
     */
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        ChatRoomMember member = chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        chatRoomMemberRepository.delete(member);

        ChatMessage exitMessage = ChatMessage.create(
                roomId,
                userId,
                user.getNickname(),
                user.getNickname() + "님이 퇴장하셨습니다.",
                MessageType.EXIT
        );
        chatMessageRepository.save(exitMessage);

        String profileImage = (user.getUserProfile() != null)
                ? user.getUserProfile().getProfileImageKey()
                : null;

        int unreadCount = getUnreadCount(roomId, exitMessage.getId());
        ChatMessageResponse response = ChatMessageResponse.from(exitMessage, profileImage, unreadCount);

        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.convertAndSend(roomId.toString(), payload);
            log.info("Leave Message Published: roomId={}, userId={}", roomId, userId);
        } catch (JsonProcessingException e) {
            log.error("Redis publish serialization failed for leave message.", e);
        }
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

        return members.stream()
                .map(member -> {
                    ChatRoom room = member.getChatRoom();
                    ChatMessage latestMsg = latestMessageMap.get(room.getId());

                    String content = (latestMsg != null) ? latestMsg.getMessage() : null;
                    LocalDateTime time = (latestMsg != null) ? latestMsg.getCreatedAt() : null;
                    int unreadCount = unreadCountMap.getOrDefault(room.getId(), 0);

                    return ChatRoomResponse.from(room, content, time, unreadCount);
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
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> findMessagesByRoomId(Long roomId, Long userId, Long lastMessageId, int size) {
        validateRoomAccess(roomId, userId);

        Pageable pageable = PageRequest.of(0, size);
        List<ChatMessage> messages = chatMessageRepository.findMessagesCursor(roomId, lastMessageId, pageable);

        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
// 1. 발신자(User) ID 목록 추출 및 일괄 조회 (N+1 방지)
Set<Long> senderIds = messages.stream()
        .map(ChatMessage::getSenderId)
        .collect(Collectors.toSet());

Map<Long, User> userMap = userRepository.findAllByIdWithProfile(senderIds).stream()
        .collect(Collectors.toMap(User::getId, user -> user));

        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomId(roomId);

        List<ChatMessageResponse> response = messages.stream()
                .map(msg -> {
                    User sender = userMap.get(msg.getSenderId());
                    String profileImage = (sender != null && sender.getUserProfile() != null)
                            ? sender.getUserProfile().getProfileImageKey()
                            : null;
                    int unreadCount = getUnreadCount(msg.getId(), members);
                    return ChatMessageResponse.from(msg, profileImage, unreadCount);
                })
                .collect(Collectors.toList());

        Collections.reverse(response);
        return response;
    }

    /**
     * 채팅방 접근 권한 확인
     */
    private void validateRoomAccess(Long roomId, Long userId) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}