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
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private NotificationService notificationService;
    @Mock
    private UserBlockPolicyService userBlockPolicyService;
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
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)).thenReturn(true);
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
    void getParticipantProfile_returnsSelectedParticipantProfile() {
        ChatService service = createService();
        Long roomId = 901L;
        Long requestUserId = 1L;
        Long targetUserId = 2L;
        User targetUser = createUser(targetUserId, "target");
        createProfile(targetUser, Gender.FEMALE);

        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, requestUserId)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, targetUserId)).thenReturn(true);
        when(userRepository.findAllByIdWithProfile(List.of(targetUserId))).thenReturn(List.of(targetUser));

        MatchingCandidateResponse response = service.getParticipantProfile(roomId, requestUserId, targetUserId);

        assertThat(response.getUserId()).isEqualTo(targetUserId);
        assertThat(response.getNickname()).isEqualTo("target");
        assertThat(response.isProfileExists()).isTrue();
        assertThat(response.getProfile()).isNotNull();
        assertThat(response.getProfile().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getProfileImage()).isEqualTo("profiles/" + targetUserId + ".png");
    }

    @Test
    void updateLastRead_marksMessagesReadAndUpdatesLastReadMessageId() {
        ChatService service = createService();
        Long roomId = 500L;
        Long userId = 10L;

        ChatRoom room = ChatRoom.create("room-500", ChatRoomType.PERSONAL);
        User user = createUser(userId, "reader");
        ChatRoomMember member = ChatRoomMember.create(room, user);
        ChatMessage lastMessage = ChatMessage.create(roomId, 20L, "sender", "hello", univ.airconnect.chat.domain.MessageType.TEXT);
        ReflectionTestUtils.setField(lastMessage, "id", 77L);

        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(member));
        when(chatMessageRepository.findUnreadIncomingMessageIds(roomId, userId)).thenReturn(List.of(11L, 12L));
        when(chatMessageRepository.findTopByRoomIdOrderByIdDesc(roomId)).thenReturn(Optional.of(lastMessage));

        service.updateLastRead(roomId, userId);

        verify(chatMessageRepository).markIncomingMessagesRead(eq(roomId), eq(userId), any(LocalDateTime.class));
        assertThat(member.getLastReadMessageId()).isEqualTo(77L);
    }

    @Test
    void deleteMessage_softDeletesOwnMessage() {
        ChatService service = createService();
        Long roomId = 99L;
        Long userId = 1L;
        Long messageId = 100L;
        User user = createUser(userId, "sender");
        ChatMessage message = ChatMessage.create(roomId, userId, "sender", "hello", univ.airconnect.chat.domain.MessageType.TEXT);
        ReflectionTestUtils.setField(message, "id", messageId);

        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)).thenReturn(true);
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = service.deleteMessage(userId, roomId, messageId);

        assertThat(response.isDeleted()).isTrue();
        assertThat(response.getContent()).isEqualTo("삭제된 메시지입니다.");
    }

    @Test
    void sendMessage_throwsWhenUserDeleted() {
        ChatService service = createService();
        Long userId = 1L;
        Long roomId = 10L;

        User user = createUser(userId, "sender");
        user.markDeleted();

        ChatMessageRequest request = new ChatMessageRequest();
        ReflectionTestUtils.setField(request, "roomId", roomId);
        ReflectionTestUtils.setField(request, "message", "hello");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.sendMessage(userId, request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.USER_DELETED);
    }

    @Test
    void findAllRooms_returnsRoomListWithCounterpartInfo() {
        ChatService service = createService();
        Long myUserId = 1L;
        Long roomId = 300L;
        User me = createUser(myUserId, "me");
        User other = createUser(2L, "other");
        createProfile(other, Gender.FEMALE);
        ChatRoom room = ChatRoom.createPersonal("소개팅 1:1", myUserId, other.getId(), 88L);
        ReflectionTestUtils.setField(room, "id", roomId);
        room.updateLastMessage("latest", LocalDateTime.now());

        ChatRoomMember myMembership = ChatRoomMember.create(room, me);
        ChatRoomMember otherMembership = ChatRoomMember.create(room, other);

        when(chatRoomMemberRepository.findByUser_IdWithRoom(myUserId)).thenReturn(List.of(myMembership));
        when(chatMessageRepository.findLatestMessagesByRoomIds(List.of(roomId))).thenReturn(List.of());
        when(chatMessageRepository.countUnreadByUserId(myUserId)).thenReturn(java.util.Collections.singletonList(new Object[]{roomId, 2L}));
        when(chatRoomMemberRepository.findByChatRoomIdInWithUser(List.of(roomId))).thenReturn(List.of(myMembership, otherMembership));

        List<ChatRoomResponse> response = service.findAllRooms(myUserId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getTargetUserId()).isEqualTo(other.getId());
        assertThat(response.get(0).getTargetNickname()).isEqualTo("other");
        assertThat(response.get(0).getUnreadCount()).isEqualTo(2);
    }

    @Test
    void findMessagesByRoomId_returnsChronologicalMessages() {
        ChatService service = createService();
        Long roomId = 700L;
        Long userId = 1L;
        User sender = createUser(2L, "sender");
        createProfile(sender, Gender.FEMALE);
        ChatMessage newer = ChatMessage.create(roomId, sender.getId(), "sender", "second", univ.airconnect.chat.domain.MessageType.TEXT);
        ChatMessage older = ChatMessage.create(roomId, sender.getId(), "sender", "first", univ.airconnect.chat.domain.MessageType.TEXT);
        ReflectionTestUtils.setField(newer, "id", 20L);
        ReflectionTestUtils.setField(older, "id", 10L);

        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)).thenReturn(true);
        when(chatMessageRepository.findMessagesCursor(eq(roomId), eq(null), any())).thenReturn(List.of(newer, older));
        when(userRepository.findAllByIdWithProfile(Set.of(sender.getId()))).thenReturn(List.of(sender));
        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        List<ChatMessageResponse> response = service.findMessagesByRoomId(roomId, userId, null, 20);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getId()).isEqualTo(10L);
        assertThat(response.get(1).getId()).isEqualTo(20L);
    }

    @Test
    void sendMessage_throwsWhenUsersAreBlockedInRoom() {
        ChatService service = createService();
        Long userId = 1L;
        Long roomId = 700L;
        Long otherUserId = 2L;
        User user = createUser(userId, "sender");

        ChatMessageRequest request = new ChatMessageRequest();
        ReflectionTestUtils.setField(request, "roomId", roomId);
        ReflectionTestUtils.setField(request, "message", "hello");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)).thenReturn(true);
        when(chatRoomMemberRepository.findUserIdsByChatRoomId(roomId)).thenReturn(List.of(userId, otherUserId));
        when(userBlockPolicyService.findAnyBlockedCounterpart(userId, List.of(otherUserId))).thenReturn(Optional.of(otherUserId));

        assertThatThrownBy(() -> service.sendMessage(userId, request))
                .isInstanceOf(univ.airconnect.global.error.BusinessException.class)
                .extracting(ex -> ((univ.airconnect.global.error.BusinessException) ex).getErrorCode())
                .isEqualTo(univ.airconnect.global.error.ErrorCode.USER_BLOCKED_INTERACTION);
    }

    private ChatService createService() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(userBlockPolicyService.hasBlockRelation(any(), any())).thenReturn(false);
        lenient().when(userBlockPolicyService.findAnyBlockedCounterpart(any(), any())).thenReturn(Optional.empty());
        ChatService service = new ChatService(
                chatRoomRepository,
                chatMessageRepository,
                chatRoomMemberRepository,
                userRepository,
                redisMessageListenerContainer,
                redisSubscriber,
                redisTemplate,
                objectMapper,
                notificationService,
                userBlockPolicyService
        );
        ReflectionTestUtils.setField(service, "imageUrlBase", "http://localhost:8080/api/v1/users/profile-images");
        return service;
    }

    private User createUser(Long userId, String nickname) {
        User user = User.create(SocialProvider.KAKAO, "social-" + userId, "u" + userId + "@test.dev");
        user.completeSignUp("name-" + userId, nickname, 20230000 + userId.intValue(), "dept");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private UserProfile createProfile(User user, Gender gender) {
        UserProfile profile = UserProfile.create(
                user,
                null,
                null,
                null,
                null,
                gender,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(profile, "userId", user.getId());
        ReflectionTestUtils.setField(user, "userProfile", profile);
        profile.updateProfileImagePath("profiles/" + user.getId() + ".png");
        return profile;
    }
}
