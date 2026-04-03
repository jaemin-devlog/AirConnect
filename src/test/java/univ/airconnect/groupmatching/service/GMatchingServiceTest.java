package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingServiceTest {

    @Mock
    private GTemporaryTeamRoomRepository temporaryTeamRoomRepository;
    @Mock
    private GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    @Mock
    private GTeamReadyStateRepository teamReadyStateRepository;
    @Mock
    private GMatchResultRepository matchResultRepository;
    @Mock
    private GFinalGroupChatRoomRepository finalGroupChatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ChatService chatService;
    @Mock
    private GMatchingEventPublisher matchingEventPublisher;
    @Mock
    private GMatchingPushService matchingPushService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createTemporaryTeamRoom_generatesInviteCodeOnCreation() {
        GMatchingService service = createService();
        Long leaderId = 10L;
        User leader = createUser(leaderId, "leader");
        UserProfile profile = createProfile(leader, Gender.MALE);
        ChatRoom tempChatRoom = mock(ChatRoom.class);

        when(userRepository.findById(leaderId)).thenReturn(Optional.of(leader));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(leaderId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(leaderId)).thenReturn(Optional.of(profile));
        when(chatService.createGroupRoomWithMembers(anyString(), any(Collection.class))).thenReturn(tempChatRoom);
        when(tempChatRoom.getId()).thenReturn(300L);
        when(temporaryTeamRoomRepository.existsUsableInviteCode(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            GTemporaryTeamRoom room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", 101L);
            return room;
        }).when(temporaryTeamRoomRepository).save(any(GTemporaryTeamRoom.class));

        GTemporaryTeamRoom created = service.createTemporaryTeamRoom(
                leaderId,
                "team-a",
                GTeamGender.M,
                GTeamSize.TWO,
                GGenderFilter.ANY,
                GTeamVisibility.PUBLIC
        );

        assertThat(created.getInviteCode()).matches("\\d{6}");
        verify(temporaryTeamRoomRepository).existsUsableInviteCode(anyString());
    }

    @Test
    void createTemporaryTeamRoom_throwsWhenLeaderGenderMismatched() {
        GMatchingService service = createService();
        Long leaderId = 10L;
        User leader = createUser(leaderId, "leader");
        UserProfile profile = createProfile(leader, Gender.FEMALE);

        when(userRepository.findById(leaderId)).thenReturn(Optional.of(leader));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(leaderId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(leaderId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.createTemporaryTeamRoom(
                leaderId,
                "team-a",
                GTeamGender.M,
                GTeamSize.TWO,
                GGenderFilter.ANY,
                GTeamVisibility.PUBLIC
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TEAM_GENDER_MISMATCH);

        verify(chatService, never()).createGroupRoomWithMembers(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void joinPublicRoom_throwsWhenJoinerGenderMismatched() {
        GMatchingService service = createService();
        Long roomId = 200L;
        Long userId = 20L;

        User joiner = createUser(userId, "joiner");
        UserProfile profile = createProfile(joiner, Gender.FEMALE);
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(teamRoom.isPublicRoom()).thenReturn(true);
        when(teamRoom.getLeaderId()).thenReturn(999L);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.OPEN);
        when(teamRoom.isFull()).thenReturn(false);
        when(teamRoom.getTeamGender()).thenReturn(GTeamGender.M);
        when(userRepository.findById(userId)).thenReturn(Optional.of(joiner));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(userId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.joinPublicRoom(roomId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TEAM_GENDER_MISMATCH);

        verify(temporaryTeamMemberRepository, never())
                .existsByTeamRoomIdAndUserIdAndLeftAtIsNull(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void joinRoomByInviteCode_allowsPublicRoomWhenInviteCodeExists() {
        GMatchingService service = createService();
        Long roomId = 200L;
        Long userId = 20L;

        User joiner = createUser(userId, "joiner");
        UserProfile profile = createProfile(joiner, Gender.MALE);
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamRoomRepository.findByInviteCode("INVITE1234")).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getId()).thenReturn(roomId);
        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getLeaderId()).thenReturn(999L);
        when(teamRoom.getStatus()).thenReturn(
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.OPEN
        );
        when(teamRoom.isFull()).thenReturn(false);
        when(teamRoom.getTeamGender()).thenReturn(GTeamGender.M);
        when(teamRoom.getTempChatRoomId()).thenReturn(300L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(joiner));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(userId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId)).thenReturn(List.of());

        GTemporaryTeamRoom joined = service.joinRoomByInviteCode("INVITE1234", userId);

        assertThat(joined).isSameAs(teamRoom);
        verify(chatService).addMembersToRoom(300L, List.of(userId));
    }

    @Test
    void joinRoomByInviteCode_rejoinsLeftMember() {
        GMatchingService service = createService();
        Long roomId = 205L;
        Long userId = 25L;

        User joiner = createUser(userId, "rejoiner");
        UserProfile profile = createProfile(joiner, Gender.MALE);
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember existingMember = mock(GTemporaryTeamMember.class);
        GTeamReadyState existingReadyState = mock(GTeamReadyState.class);

        when(temporaryTeamRoomRepository.findByInviteCode("REJOIN1234")).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getId()).thenReturn(roomId);
        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getLeaderId()).thenReturn(999L);
        when(teamRoom.getStatus()).thenReturn(
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.OPEN
        );
        when(teamRoom.isFull()).thenReturn(false);
        when(teamRoom.getTeamGender()).thenReturn(GTeamGender.M);
        when(teamRoom.getTempChatRoomId()).thenReturn(305L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(joiner));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(userId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(existingMember));
        when(existingMember.isActiveMember()).thenReturn(false);
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(existingReadyState));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId)).thenReturn(List.of());

        GTemporaryTeamRoom joined = service.joinRoomByInviteCode("REJOIN1234", userId);

        assertThat(joined).isSameAs(teamRoom);
        verify(existingMember).rejoin();
        verify(existingReadyState).markNotReady();
        verify(temporaryTeamMemberRepository, never()).save(any(GTemporaryTeamMember.class));
        verify(teamReadyStateRepository, never()).save(any(GTeamReadyState.class));
        verify(chatService).addMembersToRoom(305L, List.of(userId));
    }

    @Test
    void generateInviteCode_allowsActiveMemberForPublicRoom() {
        GMatchingService service = createService();
        Long roomId = 100L;
        Long userId = 10L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)).thenReturn(true);
        when(temporaryTeamRoomRepository.existsUsableInviteCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        GTemporaryTeamRoom updated = service.generateInviteCode(roomId, userId);

        assertThat(updated).isSameAs(teamRoom);
        verify(teamRoom).assignInviteCode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getTeamMemberProfile_returnsSelectedActiveMemberProfile() {
        GMatchingService service = createService();
        Long roomId = 108L;
        Long requestUserId = 10L;
        Long targetUserId = 20L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        User targetUser = createUser(targetUserId, "target");
        createProfile(targetUser, Gender.FEMALE);

        when(temporaryTeamRoomRepository.findById(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, requestUserId)).thenReturn(true);
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, targetUserId)).thenReturn(true);
        when(userRepository.findAllByIdWithProfile(List.of(targetUserId))).thenReturn(List.of(targetUser));

        var response = service.getTeamMemberProfile(roomId, requestUserId, targetUserId);

        assertThat(response.getUserId()).isEqualTo(targetUserId);
        assertThat(response.getNickname()).isEqualTo("target");
        assertThat(response.isProfileExists()).isTrue();
        assertThat(response.getProfile()).isNotNull();
        assertThat(response.getProfile().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getDeptName()).isEqualTo("dept");
        assertThat(response.getProfileImage()).isEqualTo("profiles/" + targetUserId + ".png");
    }

    @Test
    void updateReadyState_publishesStatusChangedEvent() {
        GMatchingService service = createService();
        Long roomId = 110L;
        Long userId = 11L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTeamReadyState readyState = mock(GTeamReadyState.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)).thenReturn(true);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.READY_CHECK);
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(readyState));
        when(readyState.isReady()).thenReturn(true);

        GTeamReadyState updated = service.updateReadyState(roomId, userId, true);

        assertThat(updated).isSameAs(readyState);
        verify(readyState).setReady(true);
        verify(matchingEventPublisher).publishStatus(roomId, GTemporaryTeamRoomStatus.READY_CHECK.name());
    }

    @Test
    void updateReadyState_notifiesLeaderWhenAllMembersReady() {
        GMatchingService service = createService();
        Long roomId = 111L;
        Long userId = 11L;
        Long leaderId = 99L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTeamReadyState readyState = mock(GTeamReadyState.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)).thenReturn(true);
        when(teamRoom.getId()).thenReturn(roomId);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.READY_CHECK);
        when(teamRoom.getLeaderId()).thenReturn(leaderId);
        when(teamRoom.getTeamSize()).thenReturn(GTeamSize.TWO);
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(readyState));
        when(readyState.isReady()).thenReturn(false);
        when(teamReadyStateRepository.areAllMembersReady(roomId, GTeamSize.TWO.getValue())).thenReturn(true);

        service.updateReadyState(roomId, userId, true);

        ArgumentCaptor<NotificationService.CreateCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService).createAndEnqueue(commandCaptor.capture());

        NotificationService.CreateCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(leaderId);
        assertThat(command.type()).isEqualTo(NotificationType.TEAM_ALL_READY);
    }

    @Test
    void updateReadyState_enqueuesPushForOtherMembersWhenReadyStateChanges() {
        GMatchingService service = createService();
        Long roomId = 112L;
        Long userId = 11L;
        Long otherUserId = 22L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTeamReadyState readyState = mock(GTeamReadyState.class);
        GTemporaryTeamMember actorMember = mock(GTemporaryTeamMember.class);
        GTemporaryTeamMember otherMember = mock(GTemporaryTeamMember.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)).thenReturn(true);
        when(teamRoom.getId()).thenReturn(roomId);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.READY_CHECK);
        when(teamRoom.getCurrentMemberCount()).thenReturn(2);
        when(teamRoom.getTeamSize()).thenReturn(GTeamSize.TWO);
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(readyState));
        when(readyState.isReady()).thenReturn(false);
        when(teamReadyStateRepository.areAllMembersReady(roomId, GTeamSize.TWO.getValue())).thenReturn(false);
        when(actorMember.getUserId()).thenReturn(userId);
        when(otherMember.getUserId()).thenReturn(otherUserId);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId))
                .thenReturn(List.of(actorMember, otherMember));

        service.updateReadyState(roomId, userId, true);

        ArgumentCaptor<NotificationService.CreateCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService).createAndEnqueue(commandCaptor.capture());

        NotificationService.CreateCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(otherUserId);
        assertThat(command.type()).isEqualTo(NotificationType.TEAM_MEMBER_READY_CHANGED);
        assertThat(command.title()).isEqualTo("팀원 준비 완료");
        assertThat(command.body()).isEqualTo("팀원이 준비를 완료했어요.");
    }

    @Test
    void joinRoomByInviteCode_notifiesReadyRequiredWhenRoomBecomesFull() {
        GMatchingService service = createService();
        Long roomId = 201L;
        Long userId = 21L;
        Long leaderId = 999L;

        User joiner = createUser(userId, "joiner");
        UserProfile profile = createProfile(joiner, Gender.MALE);
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember leaderMember = mock(GTemporaryTeamMember.class);
        GTemporaryTeamMember joinerMember = mock(GTemporaryTeamMember.class);

        when(temporaryTeamRoomRepository.findByInviteCode("INVITEFULL1")).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getId()).thenReturn(roomId);
        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(teamRoom.getLeaderId()).thenReturn(leaderId);
        when(teamRoom.getStatus()).thenReturn(
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.OPEN,
                GTemporaryTeamRoomStatus.READY_CHECK
        );
        when(teamRoom.isFull()).thenReturn(false, true);
        when(teamRoom.getTeamGender()).thenReturn(GTeamGender.M);
        when(teamRoom.getTeamSize()).thenReturn(GTeamSize.TWO);
        when(teamRoom.getTempChatRoomId()).thenReturn(301L);
        when(teamRoom.getCurrentMemberCount()).thenReturn(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(joiner));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(eq(userId), anySet())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        when(leaderMember.getUserId()).thenReturn(leaderId);
        when(joinerMember.getUserId()).thenReturn(userId);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId))
                .thenReturn(List.of(leaderMember, joinerMember));

        service.joinRoomByInviteCode("INVITEFULL1", userId);

        ArgumentCaptor<NotificationService.CreateCommand> enqueueCaptor =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notificationService, times(3)).createAndEnqueue(enqueueCaptor.capture());
        assertThat(enqueueCaptor.getAllValues())
                .extracting(NotificationService.CreateCommand::type)
                .containsExactlyInAnyOrder(
                        NotificationType.TEAM_MEMBER_JOINED,
                        NotificationType.TEAM_READY_REQUIRED,
                        NotificationType.TEAM_READY_REQUIRED
                );

        verify(teamRoom).enterReadyCheck(leaderId);
        verify(matchingEventPublisher).publishStatus(roomId, GTemporaryTeamRoomStatus.READY_CHECK.name());
    }

    @Test
    void leaveTeamRoom_returnsSuccessWhenMemberAlreadyLeft() {
        GMatchingService service = createService();
        Long roomId = 210L;
        Long userId = 21L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(false);

        GTemporaryTeamRoom leftRoom = service.leaveTeamRoom(roomId, userId);

        assertThat(leftRoom).isSameAs(teamRoom);
        verify(member, never()).markLeft();
        verify(chatService, never()).publishExitMessage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void leaveTeamRoom_enqueuesPushAndPublishesStatusChanged() {
        GMatchingService service = createService();
        Long roomId = 215L;
        Long userId = 21L;
        Long leaderId = 99L;
        Long chatRoomId = 315L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);
        GTemporaryTeamMember leaderMember = mock(GTemporaryTeamMember.class);
        GTeamReadyState readyState = mock(GTeamReadyState.class);
        User user = createUser(userId, "member");

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(true);
        when(member.isLeader()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(teamRoom.getId()).thenReturn(roomId);
        when(teamRoom.getTempChatRoomId()).thenReturn(chatRoomId);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.READY_CHECK, GTemporaryTeamRoomStatus.OPEN);
        when(teamRoom.getCurrentMemberCount()).thenReturn(2);
        when(chatService.isMember(chatRoomId, userId)).thenReturn(true);
        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(Optional.empty());
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(readyState));
        when(leaderMember.getUserId()).thenReturn(leaderId);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId))
                .thenReturn(List.of(leaderMember));

        GTemporaryTeamRoom leftRoom = service.leaveTeamRoom(roomId, userId);

        assertThat(leftRoom).isSameAs(teamRoom);
        verify(teamRoom).removeMember();
        verify(notificationService).createAndEnqueue(any(NotificationService.CreateCommand.class));
        verify(matchingEventPublisher).publishStatus(roomId, GTemporaryTeamRoomStatus.OPEN.name());
    }

    @Test
    void leaveTeamRoom_cleansUpActiveMemberEvenWhenRoomIsCancelled() {
        GMatchingService service = createService();
        Long roomId = 220L;
        Long userId = 22L;
        Long chatRoomId = 320L;
        GTemporaryTeamRoom teamRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);
        User user = createUser(userId, "member");

        when(temporaryTeamRoomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(member));
        when(member.isActiveMember()).thenReturn(true);
        when(member.isLeader()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(teamRoom.getId()).thenReturn(roomId);
        when(teamRoom.getTempChatRoomId()).thenReturn(chatRoomId);
        when(teamRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.CANCELLED);
        when(chatService.isMember(chatRoomId, userId)).thenReturn(false);
        when(chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId)).thenReturn(Optional.empty());
        when(teamReadyStateRepository.findByTeamRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(roomId)).thenReturn(List.of());

        GTemporaryTeamRoom leftRoom = service.leaveTeamRoom(roomId, userId);

        assertThat(leftRoom).isSameAs(teamRoom);
        verify(member).markLeft();
        verify(chatService, never()).publishExitMessage(eq(chatRoomId), eq(userId), org.mockito.ArgumentMatchers.anyString());
        verify(teamRoom, never()).removeMember();
    }

    @Test
    void processQueueUntilStable_repeatsUntilQueueStopsMatching() {
        GMatchingService service = spy(createService());
        GMatchingService.MatchSuccessResult first = new GMatchingService.MatchSuccessResult(1L, 2L, 3L, 4L, 5L);
        GMatchingService.MatchSuccessResult second = new GMatchingService.MatchSuccessResult(6L, 7L, 8L, 9L, 10L);

        doReturn(first, second, null).when(service).processQueue(GTeamSize.TWO, -1);

        int matchedCount = service.processQueueUntilStable(GTeamSize.TWO);

        assertThat(matchedCount).isEqualTo(2);
        verify(service, times(3)).processQueue(GTeamSize.TWO, -1);
    }

    @Test
    void reconcileQueue_rebuildsRedisQueueFromWaitingRooms() {
        GMatchingService service = createService();
        GTemporaryTeamRoom firstWaitingRoom = mock(GTemporaryTeamRoom.class);
        GTemporaryTeamRoom secondWaitingRoom = mock(GTemporaryTeamRoom.class);
        AtomicReference<Object> lockValueRef = new AtomicReference<>();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(temporaryTeamRoomRepository.findAllQueueWaitingRooms(GTeamSize.TWO))
                .thenReturn(List.of(firstWaitingRoom, secondWaitingRoom));
        when(firstWaitingRoom.getId()).thenReturn(10L);
        when(firstWaitingRoom.getQueueToken()).thenReturn("token-1");
        when(firstWaitingRoom.recoverQueueMetadata("token-1")).thenReturn(false);
        when(secondWaitingRoom.getId()).thenReturn(20L);
        when(secondWaitingRoom.getQueueToken()).thenReturn("token-2");
        when(secondWaitingRoom.recoverQueueMetadata("token-2")).thenReturn(false);
        when(listOperations.range("matching:queue:TWO", 0, -1)).thenReturn(List.of("20"));
        doAnswer(invocation -> {
            lockValueRef.set(invocation.getArgument(1));
            return true;
        }).when(valueOperations).setIfAbsent(eq("matching:queue:process-lock:TWO"), any(), eq(Duration.ofSeconds(5)));
        when(valueOperations.get("matching:queue:process-lock:TWO")).thenAnswer(invocation -> lockValueRef.get());

        GMatchingService.QueueReconcileResult result = service.reconcileQueue(GTeamSize.TWO);

        assertThat(result.lockAcquired()).isTrue();
        assertThat(result.rebuilt()).isTrue();
        assertThat(result.waitingTeamCount()).isEqualTo(2);
        verify(redisTemplate).delete("matching:queue:TWO");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Object>> queueValuesCaptor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(listOperations).rightPushAll(eq("matching:queue:TWO"), queueValuesCaptor.capture());
        assertThat(queueValuesCaptor.getValue()).containsExactly("10", "20");
        verify(valueOperations).set("matching:queue:token:token-1", "10", Duration.ofHours(12));
        verify(valueOperations).set("matching:queue:token:token-2", "20", Duration.ofHours(12));
    }

    @Test
    void reconcileQueue_recoversMissingQueueMetadata() {
        GMatchingService service = createService();
        GTemporaryTeamRoom waitingRoom = mock(GTemporaryTeamRoom.class);
        AtomicReference<Object> lockValueRef = new AtomicReference<>();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(temporaryTeamRoomRepository.findAllQueueWaitingRooms(GTeamSize.THREE)).thenReturn(List.of(waitingRoom));
        when(waitingRoom.getId()).thenReturn(30L);
        when(waitingRoom.getQueueToken()).thenReturn(null);
        when(waitingRoom.recoverQueueMetadata(anyString())).thenReturn(true);
        when(listOperations.range("matching:queue:THREE", 0, -1)).thenReturn(List.of());
        doAnswer(invocation -> {
            lockValueRef.set(invocation.getArgument(1));
            return true;
        }).when(valueOperations).setIfAbsent(eq("matching:queue:process-lock:THREE"), any(), eq(Duration.ofSeconds(5)));
        when(valueOperations.get("matching:queue:process-lock:THREE")).thenAnswer(invocation -> lockValueRef.get());

        GMatchingService.QueueReconcileResult result = service.reconcileQueue(GTeamSize.THREE);

        assertThat(result.lockAcquired()).isTrue();
        assertThat(result.metadataRecovered()).isTrue();
        verify(waitingRoom).recoverQueueMetadata(anyString());
    }

    @Test
    void getQueueSnapshot_recoversQueueWhenRedisEntryIsMissing() {
        GMatchingService service = createService();
        GTemporaryTeamRoom waitingRoom = mock(GTemporaryTeamRoom.class);
        AtomicReference<Object> lockValueRef = new AtomicReference<>();

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(temporaryTeamRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(waitingRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(10L, 99L)).thenReturn(true);
        when(waitingRoom.getId()).thenReturn(10L);
        when(waitingRoom.getStatus()).thenReturn(GTemporaryTeamRoomStatus.QUEUE_WAITING);
        when(waitingRoom.getTeamSize()).thenReturn(GTeamSize.TWO);
        when(waitingRoom.getQueueToken()).thenReturn("token-1");
        when(waitingRoom.recoverQueueMetadata("token-1")).thenReturn(false);
        when(temporaryTeamRoomRepository.findAllQueueWaitingRooms(GTeamSize.TWO)).thenReturn(List.of(waitingRoom));
        doReturn(List.of(), List.of(), List.of("10"))
                .when(listOperations).range("matching:queue:TWO", 0, -1);
        doAnswer(invocation -> {
            lockValueRef.set(invocation.getArgument(1));
            return true;
        }).when(valueOperations).setIfAbsent(eq("matching:queue:process-lock:TWO"), any(), eq(Duration.ofSeconds(5)));
        when(valueOperations.get("matching:queue:process-lock:TWO")).thenAnswer(invocation -> lockValueRef.get());

        GMatchingService.QueueSnapshot snapshot = service.getQueueSnapshot(10L, 99L);

        assertThat(snapshot.position()).isEqualTo(1);
        assertThat(snapshot.aheadCount()).isEqualTo(0);
        assertThat(snapshot.totalWaitingTeams()).isEqualTo(1);
        verify(redisTemplate).delete("matching:queue:TWO");
    }

    @Test
    void findMyActiveFinalRoom_returnsLatestActiveFinalRoomForUser() {
        GMatchingService service = createService();
        GFinalGroupChatRoomRepository finalRoomRepository = this.finalGroupChatRoomRepository;
        univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom finalRoom =
                mock(univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom.class);

        when(finalRoomRepository.findActiveRoomsByUserId(77L)).thenReturn(List.of(finalRoom));

        Optional<univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom> result =
                service.findMyActiveFinalRoom(77L);

        assertThat(result).contains(finalRoom);
    }

    @Test
    void canSubscribeTeamRoom_returnsTrueForActiveMember() {
        GMatchingService service = createService();
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(true);

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isTrue();
    }

    @Test
    void canSubscribeTeamRoom_returnsTrueForPastMemberWhenRoomClosed() {
        GMatchingService service = createService();
        GTemporaryTeamMember member = mock(GTemporaryTeamMember.class);
        GTemporaryTeamRoom room = mock(GTemporaryTeamRoom.class);

        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(false);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));
        when(temporaryTeamRoomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(GTemporaryTeamRoomStatus.CLOSED);

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isTrue();
    }

    @Test
    void canSubscribeTeamRoom_returnsFalseWhenNoMembership() {
        GMatchingService service = createService();

        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(1L, 2L)).thenReturn(false);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        boolean canSubscribe = service.canSubscribeTeamRoom(1L, 2L);

        assertThat(canSubscribe).isFalse();
    }

    private GMatchingService createService() {
        GMatchingService service = new GMatchingService(
                temporaryTeamRoomRepository,
                temporaryTeamMemberRepository,
                teamReadyStateRepository,
                matchResultRepository,
                finalGroupChatRoomRepository,
                chatRoomMemberRepository,
                userRepository,
                userProfileRepository,
                chatService,
                matchingEventPublisher,
                matchingPushService,
                notificationService,
                objectMapper,
                redisTemplate,
                analyticsService
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

