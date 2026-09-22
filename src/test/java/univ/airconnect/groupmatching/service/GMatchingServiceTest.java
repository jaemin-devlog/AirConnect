package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingServiceTest {

    @Mock GTemporaryTeamRoomRepository rooms;
    @Mock GTemporaryTeamMemberRepository members;
    @Mock GMatchResultRepository matchResults;
    @Mock GFinalGroupChatRoomRepository finalRooms;
    @Mock UserRepository users;
    @Mock UserProfileRepository profiles;
    @Mock ChatService chat;
    @Mock GMatchingEventPublisher events;
    @Mock NotificationService notifications;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ListOperations<String, Object> lists;
    @Mock ValueOperations<String, Object> values;
    @Mock AnalyticsService analytics;
    @Mock TicketLedgerRepository ticketLedger;
    @Mock PlatformTransactionManager transactions;
    @Mock UserBlockPolicyService blocks;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks GMatchingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "imageUrlBase", "http://localhost/profile-images");
        lenient().when(redis.opsForList()).thenReturn(lists);
        lenient().when(redis.opsForValue()).thenReturn(values);
        lenient().when(values.setIfAbsent(any(), any(), any())).thenReturn(true);
        lenient().when(rooms.findActiveRoomsByUserIdForUpdate(anyLong(), any())).thenReturn(List.of());
        lenient().when(rooms.existsByInviteCode(any())).thenReturn(false);
        lenient().when(rooms.save(any())).thenAnswer(invocation -> {
            GTemporaryTeamRoom room = invocation.getArgument(0);
            if (room.getId() == null) ReflectionTestUtils.setField(room, "id", 100L);
            return room;
        });
    }

    @Test
    void create_usesProfileGenderAndCreatesNoTemporaryChatOrReadyStep() {
        User leader = user(1L, "방장", 10);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(leader));
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(leader, Gender.MALE)));

        GTemporaryTeamRoom room = service.createTemporaryTeamRoom(1L, GTeamSize.TWO);

        assertThat(room.getTeamGender()).isEqualTo(GTeamGender.M);
        assertThat(room.getTeamSize()).isEqualTo(GTeamSize.TWO);
        assertThat(room.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.OPEN);
        assertThat(room.getTempChatRoomId()).isNull();
        assertThat(room.getInviteCode()).matches("\\d{6}");
        verify(chat, never()).createGroupRoomWithMembers(any(), any());
        verify(members).save(any(GTemporaryTeamMember.class));
    }

    @Test
    void joinByInvite_allowsSameGenderFriendAndFillsRoom() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.assignInviteCode("012345");
        User friend = user(2L, "친구", 10);
        when(rooms.findByInviteCode("012345")).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(friend));
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile(friend, Gender.MALE)));
        when(members.findByTeamRoomIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L)).thenReturn(List.of());

        GTemporaryTeamRoom joined = service.joinRoomByInviteCode(" 012345 ", 2L);

        assertThat(joined.isFull()).isTrue();
        assertThat(joined.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.OPEN);
        verify(members).save(any(GTemporaryTeamMember.class));
        verify(chat, never()).addMembersToRoom(anyLong(), any());
    }

    @Test
    void getTeamMemberProfile_returnsActualSchoolVerificationStatus() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        User target = user(2L, "친구", 10);
        target.updateVerifiedSchoolEmail("friend@school.ac.kr");
        UserProfile targetProfile = profile(target, Gender.MALE);
        ReflectionTestUtils.setField(target, "userProfile", targetProfile);

        when(rooms.findById(100L)).thenReturn(Optional.of(room));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 2L)).thenReturn(true);
        when(users.findAllByIdWithProfile(List.of(2L))).thenReturn(List.of(target));

        var response = service.getTeamMemberProfile(100L, 1L, 2L);

        assertThat(response.isEmailVerified()).isTrue();
        assertThat(response.getProfile()).isNotNull();
        assertThat(response.getProfile().isEmailVerified()).isTrue();
    }

    @Test
    void joinByInvite_rejectsDifferentGender() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.assignInviteCode("123456");
        User friend = user(2L, "친구", 10);
        when(rooms.findByInviteCode("123456")).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(friend));
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile(friend, Gender.FEMALE)));
        assertThatThrownBy(() -> service.joinRoomByInviteCode("123456", 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_GENDER_MISMATCH);
    }

    @Test
    void startMatching_isLeaderOnly() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.addMember();
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.startMatching(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEADER_ONLY_ACTION);
    }

    @Test
    void startMatching_rejectsWhenAnyMemberLacksTickets() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.addMember();
        List<GTemporaryTeamMember> active = List.of(
                GTemporaryTeamMember.create(100L, 1L, true),
                GTemporaryTeamMember.create(100L, 2L, false));
        User leader = user(1L, "방장", 10);
        User friend = user(2L, "친구", 1);
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L)).thenReturn(active);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(leader, Gender.MALE)));
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile(friend, Gender.MALE)));
        when(users.findById(1L)).thenReturn(Optional.of(leader));
        when(users.findById(2L)).thenReturn(Optional.of(friend));

        assertThatThrownBy(() -> service.startMatching(100L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(room.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.OPEN);
    }

    @Test
    void startMatching_notifiesNonLeaderMembers() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.addMember();
        List<GTemporaryTeamMember> active = List.of(
                GTemporaryTeamMember.create(100L, 1L, true),
                GTemporaryTeamMember.create(100L, 2L, false));
        User leader = user(1L, "방장", 10);
        User friend = user(2L, "친구", 10);

        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L)).thenReturn(active);
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile(leader, Gender.MALE)));
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile(friend, Gender.MALE)));
        when(users.findById(1L)).thenReturn(Optional.of(leader));
        when(users.findById(2L)).thenReturn(Optional.of(friend));
        when(lists.range(any(), anyLong(), anyLong())).thenReturn(List.of());
        when(rooms.findAllQueueWaitingRooms(GTeamSize.TWO)).thenReturn(List.of(room));

        GMatchingService.QueueSnapshot snapshot = service.startMatching(100L, 1L);

        assertThat(snapshot.status()).isEqualTo(GTemporaryTeamRoomStatus.QUEUE_WAITING.name());
        assertThat(snapshot.position()).isEqualTo(1);
        verify(notifications).createAndEnqueue(argThat(command ->
                command.userId().equals(2L)
                        && command.type() == NotificationType.TEAM_MATCHING_STARTED
                        && command.actorUserId().equals(1L)
                        && command.title().equals("매칭 대기 시작")
                        && command.body().equals("방장이 그룹매칭을 시작했어요.")
                        && command.deeplink().equals("airconnect://matching/team-rooms/100")
                        && command.payloadJson().contains("\"teamRoomId\":100")
                        && command.payloadJson().contains("\"position\":1")
                        && command.payloadJson().contains("\"totalWaitingTeams\":1")
                        && command.dedupeKey().startsWith("group-matching:queue-started:100:")));
    }

    @Test
    void anyMemberCanStopQueueAndTeamReturnsToOpen() {
        GTemporaryTeamRoom room = queueRoom(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 2L)).thenReturn(true);
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L)).thenReturn(List.of());
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, "친구", 10)));

        GTemporaryTeamRoom stopped = service.leaveMatchingQueue(100L, 2L);

        assertThat(stopped.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.OPEN);
        assertThat(stopped.getQueueToken()).isNull();
        assertThat(stopped.getQueuedAt()).isNull();
    }

    @Test
    void memberCannotLeaveWhileQueueIsActive() {
        GTemporaryTeamRoom room = queueRoom(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        GTemporaryTeamMember friend = GTemporaryTeamMember.create(100L, 2L, false);
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.findByTeamRoomIdAndUserId(100L, 2L)).thenReturn(Optional.of(friend));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, "친구", 10)));

        assertThatThrownBy(() -> service.leaveTeamRoom(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TEAM_ROOM_STATE_INVALID);
        assertThat(friend.isActiveMember()).isTrue();
    }

    @Test
    void lastNonLeaderLeavesThenLeaderDisbands_activeRoomDisappearsForEveryone() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.addMember();
        GTemporaryTeamMember leaderMember = GTemporaryTeamMember.create(100L, 1L, true);
        GTemporaryTeamMember friend = GTemporaryTeamMember.create(100L, 2L, false);
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(members.findByTeamRoomIdAndUserId(100L, 2L)).thenReturn(Optional.of(friend));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, "친구", 10)));
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(leaderMember), List.of(leaderMember));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.of(user(1L, "방장", 10)));

        service.leaveTeamRoom(100L, 2L);
        service.cancelTeamRoom(100L, 1L);

        assertThat(friend.isActiveMember()).isFalse();
        assertThat(leaderMember.isActiveMember()).isFalse();
        assertThat(room.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.CANCELLED);
        verify(values, never()).setIfAbsent(any(), any(), any());
        when(rooms.findActiveRoomsByUserId(anyLong(), any())).thenReturn(List.of());
        assertThat(service.findMyActiveTeamRoom(1L)).isEmpty();
        assertThat(service.findMyActiveTeamRoom(2L)).isEmpty();
    }

    @Test
    void queuePositionCountsOnlySameSizeAndSameGenderTeams() {
        GTemporaryTeamRoom ahead = queueRoom(90L, 9L, GTeamGender.M, GTeamSize.TWO);
        GTemporaryTeamRoom opposite = queueRoom(95L, 8L, GTeamGender.F, GTeamSize.TWO);
        GTemporaryTeamRoom mine = queueRoom(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(mine));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(rooms.findAllQueueWaitingRooms(GTeamSize.TWO)).thenReturn(List.of(ahead, opposite, mine));

        GMatchingService.QueueSnapshot snapshot = service.getQueueSnapshot(100L, 1L);

        assertThat(snapshot.position()).isEqualTo(2);
        assertThat(snapshot.aheadCount()).isEqualTo(1);
        assertThat(snapshot.totalWaitingTeams()).isEqualTo(2);
    }

    @Test
    void stoppingQueuePublishesNewPositionsForEveryRemainingTeam() {
        GTemporaryTeamRoom stopping = queueRoom(80L, 8L, GTeamGender.M, GTeamSize.TWO);
        GTemporaryTeamRoom firstMale = queueRoom(90L, 9L, GTeamGender.M, GTeamSize.TWO);
        GTemporaryTeamRoom female = queueRoom(95L, 7L, GTeamGender.F, GTeamSize.TWO);
        GTemporaryTeamRoom secondMale = queueRoom(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        when(rooms.findByIdForUpdate(80L)).thenReturn(Optional.of(stopping));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(80L, 8L)).thenReturn(true);
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(80L)).thenReturn(List.of());
        when(users.findById(8L)).thenReturn(Optional.of(user(8L, "중지", 10)));
        when(rooms.findAllQueueWaitingRooms(GTeamSize.TWO))
                .thenReturn(List.of(firstMale, female, secondMale));

        service.leaveMatchingQueue(80L, 8L);

        var snapshotCaptor = org.mockito.ArgumentCaptor.forClass(GMatchingService.QueueSnapshot.class);
        verify(events, times(3)).publishQueueSnapshot(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getAllValues())
                .extracting(GMatchingService.QueueSnapshot::teamRoomId,
                        GMatchingService.QueueSnapshot::position,
                        GMatchingService.QueueSnapshot::aheadCount,
                        GMatchingService.QueueSnapshot::totalWaitingTeams)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(90L, 1, 0, 2),
                        org.assertj.core.groups.Tuple.tuple(95L, 1, 0, 1),
                        org.assertj.core.groups.Tuple.tuple(100L, 2, 1, 2)
                );
    }

    private GTemporaryTeamRoom room(Long id, Long leaderId, GTeamGender gender, GTeamSize size) {
        GTemporaryTeamRoom room = GTemporaryTeamRoom.createInviteOnly(leaderId, gender, size);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    @Test
    void invitationCannotBypassBlockBetweenCurrentMemberAndJoiningUser() {
        GTemporaryTeamRoom room = room(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        room.assignInviteCode("123456");
        User joiner = user(2L, "joiner", 10);
        when(rooms.findByInviteCode("123456")).thenReturn(Optional.of(room));
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(room));
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(joiner));
        when(profiles.findByUserId(2L)).thenReturn(Optional.of(profile(joiner, Gender.MALE)));
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(GTemporaryTeamMember.create(100L, 1L, true)));
        when(blocks.findAnyBlockedCounterpart(2L, List.of(1L))).thenReturn(Optional.of(1L));

        assertThatThrownBy(() -> service.joinRoomByInviteCode("123456", 2L))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED_INTERACTION);
        assertThat(room.getCurrentMemberCount()).isEqualTo(1);
        verify(members, never()).save(any());
        verify(notifications, never()).createAndEnqueue(any());
    }

    @Test
    void existingTeamMembershipDoesNotPermitBlockedProfileRead() {
        when(rooms.findById(100L)).thenReturn(Optional.of(room(100L, 1L, GTeamGender.M, GTeamSize.TWO)));
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 1L)).thenReturn(true);
        when(members.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(100L, 2L)).thenReturn(true);
        when(blocks.hasBlockRelation(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.getTeamMemberProfile(100L, 1L, 2L))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED_INTERACTION);
        verify(users, never()).findAllByIdWithProfile(any());
    }

    @Test
    void blockedTeamsRemainQueuedWithoutSharedRoomOrTicketCharge() {
        GTemporaryTeamRoom first = queueRoom(100L, 1L, GTeamGender.M, GTeamSize.TWO);
        GTemporaryTeamRoom second = queueRoom(200L, 3L, GTeamGender.F, GTeamSize.TWO);
        when(lists.range(any(), anyLong(), anyLong())).thenReturn(List.of(100L, 200L));
        when(rooms.findByIdForUpdate(100L)).thenReturn(Optional.of(first));
        when(rooms.findByIdForUpdate(200L)).thenReturn(Optional.of(second));
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(100L)).thenReturn(List.of(
                GTemporaryTeamMember.create(100L, 1L, true), GTemporaryTeamMember.create(100L, 2L, false)));
        when(members.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(200L)).thenReturn(List.of(
                GTemporaryTeamMember.create(200L, 3L, true), GTemporaryTeamMember.create(200L, 4L, false)));
        when(blocks.findAnyBlockedCounterpart(1L, List.of(1L, 2L, 3L, 4L))).thenReturn(Optional.of(3L));

        assertThat(service.processQueue(GTeamSize.TWO, -1)).isNull();
        assertThat(first.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.QUEUE_WAITING);
        assertThat(second.getStatus()).isEqualTo(GTemporaryTeamRoomStatus.QUEUE_WAITING);
        verify(matchResults, never()).save(any());
        verify(chat, never()).createGroupRoomWithMembers(any(), any());
        verify(ticketLedger, never()).save(any());
    }

    private GTemporaryTeamRoom queueRoom(Long id, Long leaderId, GTeamGender gender, GTeamSize size) {
        GTemporaryTeamRoom room = room(id, leaderId, gender, size);
        while (!room.isFull()) room.addMember();
        room.startQueue(leaderId, "queue-" + id);
        return room;
    }

    private User user(Long id, String nickname, int tickets) {
        return User.builder().id(id).provider(SocialProvider.KAKAO).socialId("social-" + id)
                .nickname(nickname).status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                .tickets(tickets).createdAt(LocalDateTime.now()).build();
    }

    private UserProfile profile(User user, Gender gender) {
        return UserProfile.create(user, 175, 23, "INTJ", "NO", gender, null, null, null, null);
    }
}
