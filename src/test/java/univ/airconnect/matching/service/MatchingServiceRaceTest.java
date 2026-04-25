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
import org.springframework.dao.DataIntegrityViolationException;
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
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

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
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private ChatService chatService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserBlockPolicyService userBlockPolicyService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchingService, "imageUrlBase", "http://localhost:8080/api/v1/users/profile-images");
    }

    @Test
    @DisplayName("connect 저장 시 unique 충돌이 나도 기존 PENDING을 재조회해 의미 있는 에러를 반환한다")
    void connect_raceOnInsert_returnsAlreadyConnected() {
        Long userId = 1L;
        Long targetUserId = 2L;

        User requester = testUser(userId, 10);
        User target = testUser(targetUserId, 10);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(userBlockPolicyService.hasBlockRelation(userId, targetUserId)).thenReturn(false);
        when(matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, targetUserId)).thenReturn(true);
        when(matchingConnectionRepository.findByUser1IdAndUser2Id(1L, 2L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(MatchingConnection.createPending(userId, targetUserId)));
        when(matchingConnectionRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> matchingService.connect(userId, targetUserId))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_CONNECTED);

        assertThat(requester.getTickets()).isEqualTo(10);
    }

    @Test
    @DisplayName("connect 저장 충돌 후 기존 ACCEPTED가 비활성 채팅이면 재요청으로 전환하고 티켓 2를 차감한다")
    void connect_raceOnInsert_reopensClosedAccepted() {
        Long userId = 1L;
        Long targetUserId = 2L;

        User requester = testUser(userId, 10);
        User target = testUser(targetUserId, 10);
        MatchingConnection accepted = MatchingConnection.createPending(userId, targetUserId);
        ReflectionTestUtils.setField(accepted, "id", 55L);
        accepted.accept(99L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(userBlockPolicyService.hasBlockRelation(userId, targetUserId)).thenReturn(false);
        when(matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, targetUserId)).thenReturn(true);
        when(matchingConnectionRepository.findByUser1IdAndUser2Id(1L, 2L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(accepted));
        when(matchingConnectionRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(99L, 1L)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserId(99L, 2L)).thenReturn(false);

        MatchingConnectResponse response = matchingService.connect(userId, targetUserId);

        assertThat(response.isAlreadyConnected()).isFalse();
        assertThat(requester.getTickets()).isEqualTo(8);
        assertThat(accepted.getStatus()).isEqualTo(ConnectionStatus.PENDING);
    }

    private User testUser(Long id, int tickets) {
        return User.builder()
                .id(id)
                .provider(univ.airconnect.auth.domain.entity.SocialProvider.KAKAO)
                .socialId("s-" + id)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build();
    }
}
