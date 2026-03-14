package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
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
     */
    @Transactional
    public ChatRoomResponse createChatRoom(String name, ChatRoomType type, Long creatorUserId) {
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        ChatRoom chatRoom = ChatRoom.create(name, type);
        chatRoomRepository.save(chatRoom);
        chatRoomMemberRepository.save(ChatRoomMember.create(chatRoom, creator));

        return ChatRoomResponse.from(chatRoom);
    }

    /**
     * 채팅방 참여
     */
    @Transactional
    public void joinRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

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
     * 특정 채팅방의 읽음 상태 업데이트 (마지막 읽은 메시지 ID 갱신)
     */
    @Transactional
    public void updateLastRead(Long roomId, Long userId) {
        chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)
                .ifPresent(member -> {
                    chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(roomId)
                            .ifPresent(lastMsg -> member.updateLastReadMessageId(lastMsg.getId()));
                });
    }

    /**
     * 특정 메시지의 안읽은 사람 수 계산
     */
    public int getUnreadCount(Long roomId, Long messageId) {
        List<ChatRoomMember> members = chatRoomMemberRepository.findByChatRoomId(roomId);
        return (int) members.stream()
                .filter(m -> m.getLastReadMessageId() == null || m.getLastReadMessageId() < messageId)
                .count();
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
                request.getType()
        );
        chatMessageRepository.save(chatMessage);
        
        // 보낸 사람의 읽음 상태 즉시 업데이트
        updateLastRead(request.getRoomId(), userId);

        String profileImage = (user.getUserProfile() != null) ? user.getUserProfile().getProfileImageKey() : null;
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

        // 퇴장 메시지 생성 및 저장
        ChatMessage exitMessage = ChatMessage.create(
                roomId,
                userId,
                user.getNickname(),
                user.getNickname() + "님이 퇴장하셨습니다.",
                univ.airconnect.chat.domain.MessageType.EXIT
        );
        chatMessageRepository.save(exitMessage);

        String profileImage = (user.getUserProfile() != null) ? user.getUserProfile().getProfileImageKey() : null;
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
        return chatRoomMemberRepository.findByUser_IdOrderByJoinedAtDesc(userId).stream()
                .map(ChatRoomMember::getChatRoom)
                .map(ChatRoomResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 채팅방 메시지 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> findMessagesByRoomId(Long roomId, Long userId) {
        validateRoomAccess(roomId, userId);

        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(msg -> {
                    User sender = userRepository.findById(msg.getSenderId()).orElse(null);
                    String profileImage = (sender != null && sender.getUserProfile() != null) 
                            ? sender.getUserProfile().getProfileImageKey() : null;
                    int unreadCount = getUnreadCount(roomId, msg.getId());
                    return ChatMessageResponse.from(msg, profileImage, unreadCount);
                })
                .collect(Collectors.toList());
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
