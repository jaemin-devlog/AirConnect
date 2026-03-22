package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisMessageListenerContainer redisMessageListenerContainer;
    @Mock
    private RedisSubscriber redisSubscriber;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private SetOperations<String, Object> setOperations;

    @Test
    void sendMessage_doesNotFailWhenRedisPublishSerializationFails() throws Exception {
        ChatService service = createService();
        Long userId = 1L;
        Long roomId = 99L;

        User user = createUser(userId, "sender");
        ChatMessageRequest request = new ChatMessageRequest();
        ReflectionTestUtils.setField(request, "roomId", roomId);
        ReflectionTestUtils.setField(request, "message", "hello");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)).thenReturn(true);
        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(ChatRoom.create("r-99", ChatRoomType.PERSONAL)));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        assertThatCode(() -> service.sendMessage(userId, request)).doesNotThrowAnyException();
        verify(chatMessageRepository).save(any());
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    @Test
    void removeSessionInfo_unregistersTopicWhenNoSessionRemains() {
        ChatService service = createService();
        String sessionId = "s-1";
        String roomId = "123";

        service.enterChatRoom(roomId);
        service.mapSessionToRoom(sessionId, roomId);

        when(valueOperations.get("chat:session-room:" + sessionId)).thenReturn(roomId);
        when(setOperations.size("chat:room-sessions:" + roomId)).thenReturn(0L);

        service.removeSessionInfo(sessionId);

        verify(setOperations).remove("chat:room-sessions:" + roomId, sessionId);
        verify(redisMessageListenerContainer).removeMessageListener(
                org.mockito.ArgumentMatchers.eq(redisSubscriber),
                org.mockito.ArgumentMatchers.any(Topic.class)
        );
    }

    private ChatService createService() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        return new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                chatRoomMemberRepository,
                userRepository,
                redisMessageListenerContainer,
                redisSubscriber,
                redisTemplate,
                objectMapper
        );
    }

    private User createUser(Long userId, String nickname) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.dev");
        user.completeSignUp("name-" + userId, nickname, 20230000 + userId.intValue(), "dept");
        return user;
    }
}
