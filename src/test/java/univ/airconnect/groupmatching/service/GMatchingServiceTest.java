package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
    private univ.airconnect.chat.repository.ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
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
    private AnalyticsService analyticsService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GMatchingService matchingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                matchingService,
                "imageUrlBase",
                "http://localhost:8080/api/v1/users/profile-images"
        );
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(chatRoomMemberRepository.findByChatRoomIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("ready fails when user does not have enough tickets")
    void updateReadyState_whenTicketsAreInsufficient_throwsError() {
        Long teamRoomId = 10L;
        Long userId = 2L;

        GTemporaryTeamRoom teamRoom = createQueueReadyRoom(teamRoomId, 1L, 100L, GTeamSize.TWO, GTeamGender.M);
        teamRoom.leaveQueue();
        when(temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser(userId, "user-2", 1)));

        assertThatThrownBy(() -> matchingService.updateReadyState(teamRoomId, userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("2v2 match consumes two tickets and uses member nicknames for room name")
    void completeMatch_twoByTwo_consumesTwoTicketsAndUsesNicknameRoomName() {
        User user1 = testUser(1L, "min", 5);
        User user2 = testUser(2L, "jin", 5);
        User user3 = testUser(3L, "kang", 5);
        User user4 = testUser(4L, "seo", 5);

        GTemporaryTeamRoom firstRoom = createQueueReadyRoom(101L, 1L, 1001L, GTeamSize.TWO, GTeamGender.M);
        GTemporaryTeamRoom secondRoom = createQueueReadyRoom(202L, 3L, 2002L, GTeamSize.TWO, GTeamGender.F);

        List<GTemporaryTeamMember> firstMembers = List.of(
                GTemporaryTeamMember.create(firstRoom.getId(), 1L, true),
                GTemporaryTeamMember.create(firstRoom.getId(), 2L, false)
        );
        List<GTemporaryTeamMember> secondMembers = List.of(
                GTemporaryTeamMember.create(secondRoom.getId(), 3L, true),
                GTemporaryTeamMember.create(secondRoom.getId(), 4L, false)
        );

        stubCompleteMatchCommon(
                List.of(user1, user2, user3, user4),
                firstRoom,
                secondRoom,
                firstMembers,
                secondMembers,
                700L,
                800L
        );

        GMatchingService.MatchSuccessResult result = ReflectionTestUtils.invokeMethod(
                matchingService,
                "completeMatch",
                firstRoom,
                secondRoom
        );

        ArgumentCaptor<String> roomNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatService).createGroupRoomWithMembers(roomNameCaptor.capture(), anyCollection());

        assertThat(roomNameCaptor.getValue()).isEqualTo("min, jin, kang, seo");
        assertThat(user1.getTickets()).isEqualTo(3);
        assertThat(user2.getTickets()).isEqualTo(3);
        assertThat(user3.getTickets()).isEqualTo(3);
        assertThat(user4.getTickets()).isEqualTo(3);
        assertThat(result.finalGroupRoomId()).isEqualTo(800L);
        assertThat(result.finalChatRoomId()).isEqualTo(700L);
    }

    @Test
    @DisplayName("3v3 match consumes three tickets per member")
    void completeMatch_threeByThree_consumesThreeTickets() {
        User user1 = testUser(11L, "one", 5);
        User user2 = testUser(12L, "two", 5);
        User user3 = testUser(13L, "three", 5);
        User user4 = testUser(21L, "four", 5);
        User user5 = testUser(22L, "five", 5);
        User user6 = testUser(23L, "six", 5);

        GTemporaryTeamRoom firstRoom = createQueueReadyRoom(301L, 11L, 3001L, GTeamSize.THREE, GTeamGender.M);
        GTemporaryTeamRoom secondRoom = createQueueReadyRoom(302L, 21L, 3002L, GTeamSize.THREE, GTeamGender.F);

        List<GTemporaryTeamMember> firstMembers = List.of(
                GTemporaryTeamMember.create(firstRoom.getId(), 11L, true),
                GTemporaryTeamMember.create(firstRoom.getId(), 12L, false),
                GTemporaryTeamMember.create(firstRoom.getId(), 13L, false)
        );
        List<GTemporaryTeamMember> secondMembers = List.of(
                GTemporaryTeamMember.create(secondRoom.getId(), 21L, true),
                GTemporaryTeamMember.create(secondRoom.getId(), 22L, false),
                GTemporaryTeamMember.create(secondRoom.getId(), 23L, false)
        );

        stubCompleteMatchCommon(
                List.of(user1, user2, user3, user4, user5, user6),
                firstRoom,
                secondRoom,
                firstMembers,
                secondMembers,
                701L,
                801L
        );

        ReflectionTestUtils.invokeMethod(matchingService, "completeMatch", firstRoom, secondRoom);

        assertThat(user1.getTickets()).isEqualTo(2);
        assertThat(user2.getTickets()).isEqualTo(2);
        assertThat(user3.getTickets()).isEqualTo(2);
        assertThat(user4.getTickets()).isEqualTo(2);
        assertThat(user5.getTickets()).isEqualTo(2);
        assertThat(user6.getTickets()).isEqualTo(2);
    }

    @Test
    @DisplayName("duplicate active room names are rejected")
    void createTemporaryTeamRoom_whenActiveRoomNameExists_throwsError() {
        Long leaderUserId = 41L;
        User leader = testUser(leaderUserId, "leader", 5);

        when(userRepository.findById(leaderUserId)).thenReturn(Optional.of(leader));
        when(temporaryTeamRoomRepository.findActiveRoomsByUserId(leaderUserId, anyCollection())).thenReturn(List.of());
        when(userProfileRepository.findByUserId(leaderUserId)).thenReturn(Optional.of(profileWithGender(leader, Gender.MALE)));
        when(temporaryTeamRoomRepository.existsActiveRoomByTeamName(eq("dup-room"), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> matchingService.createTemporaryTeamRoom(
                leaderUserId,
                "  dup-room  ",
                GTeamGender.M,
                GTeamSize.TWO,
                GGenderFilter.ANY,
                GTeamVisibility.PUBLIC
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID);
    }

    @Test
    @DisplayName("main count returns all active temporary rooms")
    void countRecruitableTeamRooms_returnsActiveRoomCountForMain() {
        when(temporaryTeamRoomRepository.countActiveRooms(anyCollection())).thenReturn(7L);

        long count = matchingService.countRecruitableTeamRooms();

        assertThat(count).isEqualTo(7L);
    }

    private void stubCompleteMatchCommon(
            List<User> users,
            GTemporaryTeamRoom firstRoom,
            GTemporaryTeamRoom secondRoom,
            List<GTemporaryTeamMember> firstMembers,
            List<GTemporaryTeamMember> secondMembers,
            Long finalChatRoomId,
            Long finalGroupRoomId
    ) {
        when(matchResultRepository.existsByTeamPairAndStatuses(anyLong(), anyLong(), any())).thenReturn(false);
        when(finalGroupChatRoomRepository.findByTeamPair(firstRoom.getId(), secondRoom.getId())).thenReturn(Optional.empty());
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(firstRoom.getId()))
                .thenReturn(firstMembers);
        when(temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(secondRoom.getId()))
                .thenReturn(secondMembers);
        when(userRepository.findAllById(any(Collection.class))).thenReturn(users);

        for (User user : users) {
            when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        }

        when(matchResultRepository.save(any(GMatchResult.class))).thenAnswer(invocation -> {
            GMatchResult matchResult = invocation.getArgument(0);
            ReflectionTestUtils.setField(matchResult, "id", 900L);
            return matchResult;
        });

        ChatRoom finalChatRoom = ChatRoom.create("final-room", ChatRoomType.GROUP);
        ReflectionTestUtils.setField(finalChatRoom, "id", finalChatRoomId);
        when(chatService.createGroupRoomWithMembers(any(String.class), anyCollection())).thenReturn(finalChatRoom);

        when(finalGroupChatRoomRepository.save(any(GFinalGroupChatRoom.class))).thenAnswer(invocation -> {
            GFinalGroupChatRoom finalGroupChatRoom = invocation.getArgument(0);
            ReflectionTestUtils.setField(finalGroupChatRoom, "id", finalGroupRoomId);
            return finalGroupChatRoom;
        });
    }

    private GTemporaryTeamRoom createQueueReadyRoom(
            Long roomId,
            Long leaderId,
            Long tempChatRoomId,
            GTeamSize teamSize,
            GTeamGender teamGender
    ) {
        GTemporaryTeamRoom room = GTemporaryTeamRoom.create(
                leaderId,
                "team-" + roomId,
                teamGender,
                teamSize,
                GGenderFilter.ANY,
                GTeamVisibility.PUBLIC,
                tempChatRoomId
        );
        ReflectionTestUtils.setField(room, "id", roomId);

        while (!room.isFull()) {
            room.addMember();
        }

        room.enterReadyCheck(leaderId);
        room.startQueue(leaderId, true, "queue-token-" + roomId);
        return room;
    }

    private User testUser(Long id, String nickname, int tickets) {
        return User.builder()
                .id(id)
                .provider(SocialProvider.KAKAO)
                .socialId("social-" + id)
                .nickname(nickname)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build();
    }

    private UserProfile profileWithGender(User user, Gender gender) {
        return UserProfile.create(
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
    }
}
