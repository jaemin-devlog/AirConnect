package univ.airconnect.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.dto.response.MatchingConnectResponse;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingConnectRequestRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingRecommendationRequestRepository;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceRaceTest {

    @Mock
    private MatchingExposureRepository matchingExposureRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private MatchingConnectRequestRepository matchingConnectRequestRepository;
    @Mock
    private MatchingRecommendationRequestRepository matchingRecommendationRequestRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ChatService chatService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private univ.airconnect.matching.repository.MatchingNotificationEventRepository matchingNotificationEventRepository;
    @Mock
    private UserBlockPolicyService userBlockPolicyService;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingService, "imageUrlBase", "http://localhost:8080/api/v1/users/profile-images");
    }

    @Test
    @DisplayName("connect는 사용자 쌍을 잠근 뒤 기존 PENDING이면 티켓을 차감하지 않는다")
    void connect_lockedPairWithPending_returnsAlreadyConnected() {
        Long userId = 1L;
        Long targetUserId = 2L;

        User requester = testUser(userId, 10);
        User target = testUser(targetUserId, 10);

        when(userRepository.findByIdForTicketUpdate(userId)).thenReturn(Optional.of(requester));
        when(userRepository.findByIdForTicketUpdate(targetUserId)).thenReturn(Optional.of(target));
        when(userBlockPolicyService.hasBlockRelation(userId, targetUserId)).thenReturn(false);
        when(matchingConnectionRepository.findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(1L, 2L))
                .thenReturn(List.of(MatchingConnection.createPending(userId, targetUserId)));

        assertThatThrownBy(() -> matchingService.connect(userId, targetUserId))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_CONNECTED);

        assertThat(requester.getTickets()).isEqualTo(10);
    }

    @Test
    @DisplayName("종료된 ACCEPTED 이후 재요청은 과거 행을 변경하지 않고 새 요청 ID를 만든다")
    void connect_afterClosedAccepted_createsNewRequest() {
        Long userId = 1L;
        Long targetUserId = 2L;

        User requester = testUser(userId, 10);
        User target = testUser(targetUserId, 10);
        MatchingConnection accepted = MatchingConnection.createPending(userId, targetUserId);
        ReflectionTestUtils.setField(accepted, "id", 55L);
        accepted.accept(99L);

        when(userRepository.findByIdForTicketUpdate(userId)).thenReturn(Optional.of(requester));
        when(userRepository.findByIdForTicketUpdate(targetUserId)).thenReturn(Optional.of(target));
        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(userBlockPolicyService.hasBlockRelation(userId, targetUserId)).thenReturn(false);
        when(matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, targetUserId)).thenReturn(true);
        when(matchingConnectionRepository.findByUser1IdAndUser2IdOrderByConnectedAtDescIdDesc(1L, 2L))
                .thenReturn(List.of(accepted));
        when(matchingConnectionRepository.save(any(MatchingConnection.class))).thenAnswer(invocation -> {
            MatchingConnection created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 56L);
            return created;
        });
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(99L, 1L)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(99L, 2L)).thenReturn(false);

        MatchingConnectResponse response = matchingService.connect(userId, targetUserId);

        assertThat(response.isAlreadyConnected()).isFalse();
        assertThat(requester.getTickets()).isEqualTo(8);
        assertThat(accepted.getStatus()).isEqualTo(ConnectionStatus.ACCEPTED);
    }

    private User testUser(Long id, int tickets) {
        User user = User.builder()
                .id(id)
                .provider(univ.airconnect.auth.domain.entity.SocialProvider.KAKAO)
                .socialId("s-" + id)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build();
        UserProfile profile = UserProfile.create(
                user, 175, 22, "INTJ", "NO", Gender.MALE,
                MilitaryStatus.NOT_APPLICABLE, "NONE", "Seosan", "intro", null
        );
        when(userProfileRepository.findByUserId(id)).thenReturn(Optional.of(profile));
        return user;
    }
}
