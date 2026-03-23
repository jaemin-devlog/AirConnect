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
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private UserMilestoneRepository userMilestoneRepository;
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

    @Test
    void createOrGetPersonalRoomForConnection_restoresMissingMemberWhenReusingExistingRoom() {
        ChatService service = createService();

        Long connectionId = 77L;
        Long userA = 1L;
        Long userB = 2L;
        User a = createUser(userA, "a");
        User b = createUser(userB, "b");
        ChatRoom room = ChatRoom.createPersonal("소개팅 1:1", userA, userB, null);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "id", 555L);

        when(userRepository.findById(userA)).thenReturn(Optional.of(a));
        when(userRepository.findById(userB)).thenReturn(Optional.of(b));
        when(chatRoomRepository.findByConnectionId(connectionId)).thenReturn(Optional.empty());
        when(chatRoomRepository.findByTypeAndUser1IdAndUser2Id(ChatRoomType.PERSONAL, 1L, 2L))
                .thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(555L, userA)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(555L, userB)).thenReturn(false);
        when(userRepository.findAllById(List.of(userB))).thenReturn(List.of(b));

        ChatRoomResponse response = service.createOrGetPersonalRoomForConnection(connectionId, userA, userB, "소개팅 1:1");

        verify(chatRoomMemberRepository).saveAll(any());
        assertThat(response.getId()).isEqualTo(555L);
    }

    @Test
    void getCounterpartProfile_returnsOtherUserForPersonalRoom() {
        ChatService service = createService();

        Long roomId = 11L;
        Long myUserId = 1L;
        Long otherUserId = 2L;

        ChatRoom room = ChatRoom.createPersonal("소개팅 1:1", myUserId, otherUserId, null);
        ReflectionTestUtils.setField(room, "id", roomId);
        User other = createUser(otherUserId, "other");

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, myUserId)).thenReturn(true);
        when(userRepository.findByIdWithProfile(otherUserId)).thenReturn(Optional.of(other));
        when(userMilestoneRepository.existsByUserIdAndMilestoneType(any(), any())).thenReturn(false);

        MatchingCandidateResponse response = service.getCounterpartProfile(roomId, myUserId);

        assertThat(response.getUserId()).isEqualTo(otherUserId);
        assertThat(response.getNickname()).isEqualTo("other");
    }

    private ChatService createService() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        return new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                chatRoomMemberRepository,
                userRepository,
                userMilestoneRepository,
                redisMessageListenerContainer,
                redisSubscriber,
                redisTemplate,
                objectMapper
        );
    }

    private User createUser(Long userId, String nickname) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.dev");
        ReflectionTestUtils.setField(user, "id", userId);
        user.completeSignUp("name-" + userId, nickname, 20230000 + userId.intValue(), "dept");
        return user;
    }
}
