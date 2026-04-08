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
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
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
    @DisplayName("준비완료 시 요구 티켓보다 적으면 에러가 발생한다")
    void updateReadyState_whenTicketsAreInsufficient_throwsError() {
        Long teamRoomId = 10L;
        Long userId = 2L;

        GTemporaryTeamRoom teamRoom = createQueueReadyRoom(teamRoomId, 1L, 100L, GTeamSize.TWO, GTeamGender.M);
        teamRoom.leaveQueue();
        when(temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)).thenReturn(Optional.of(teamRoom));
        when(temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser(userId, "지인", 1)));

        assertThatThrownBy(() -> matchingService.updateReadyState(teamRoomId, userId, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("2대2 매칭 완료 시 각 멤버 티켓이 2개 차감되고 채팅방 이름은 닉네임 목록이다")
    void completeMatch_twoByTwo_consumesTwoTicketsAndUsesNicknameRoomName() {
        User user1 = testUser(1L, "재민", 5);
        User user2 = testUser(2L, "지인", 5);
        User user3 = testUser(3L, "강현", 5);
        User user4 = testUser(4L, "서연", 5);

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

        assertThat(roomNameCaptor.getValue()).isEqualTo("재민, 지인, 강현, 서연");
        assertThat(user1.getTickets()).isEqualTo(3);
        assertThat(user2.getTickets()).isEqualTo(3);
        assertThat(user3.getTickets()).isEqualTo(3);
        assertThat(user4.getTickets()).isEqualTo(3);
        assertThat(result.finalGroupRoomId()).isEqualTo(800L);
        assertThat(result.finalChatRoomId()).isEqualTo(700L);
    }

    @Test
    @DisplayName("3대3 매칭 완료 시 각 멤버 티켓이 3개 차감된다")
    void completeMatch_threeByThree_consumesThreeTickets() {
        User user1 = testUser(11L, "하나", 5);
        User user2 = testUser(12L, "둘", 5);
        User user3 = testUser(13L, "셋", 5);
        User user4 = testUser(21L, "넷", 5);
        User user5 = testUser(22L, "다섯", 5);
        User user6 = testUser(23L, "여섯", 5);

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
}
