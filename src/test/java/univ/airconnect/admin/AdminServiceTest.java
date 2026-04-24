package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserReportRepository userReportRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private StatisticsService statisticsService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                userReportRepository,
                matchingConnectionRepository,
                ticketLedgerRepository,
                userService,
                notificationService,
                statisticsService,
                new ObjectMapper()
        );
    }

    @Test
    void adjustTickets_updatesBalanceAndCreatesLedger() {
        User user = user(1L, 10);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        AdminDtos.TicketBalance response = adminService.adjustTickets(
                new AdminRequests.TicketAdjustmentRequest(1L, 5, "bug compensation")
        );

        ArgumentCaptor<TicketLedger> captor = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ticketLedgerRepository).save(captor.capture());
        assertThat(response.currentTickets()).isEqualTo(15);
        assertThat(captor.getValue().getChangeAmount()).isEqualTo(5);
        assertThat(captor.getValue().getAfterAmount()).isEqualTo(15);
        assertThat(captor.getValue().getReason()).isEqualTo("ADMIN:bug compensation");
    }

    @Test
    void applyUserAction_restrictMatching_marksUserRestricted() {
        User user = user(2L, 10);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userReportRepository.countByReportedUserIdAndStatus(2L, ReportStatus.OPEN)).thenReturn(0L);

        AdminDtos.UserDetail response = adminService.applyUserAction(
                2L,
                new AdminRequests.UserActionRequest(
                        AdminRequests.UserActionType.RESTRICT_MATCHING,
                        "abuse",
                        LocalDateTime.now().plusDays(3)
                )
        );

        assertThat(user.isMatchingRestricted()).isTrue();
        assertThat(response.restrictedReason()).isEqualTo("abuse");
    }

    @Test
    void broadcastNotice_sendsSystemAnnouncementToActiveUsers() {
        when(userRepository.findIdsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(10L, 20L));

        AdminDtos.NoticeBroadcastResult response = adminService.broadcastNotice(
                new AdminRequests.NoticeBroadcastRequest("공지", "점검 예정", "airconnect://notice", true)
        );

        verify(notificationService, times(2)).createAndEnqueue(any());
        assertThat(response.recipients()).isEqualTo(2);
    }

    @Test
    void getStatisticsOverview_combinesExistingStatsAndTicketData() {
        MainStatisticsResponse main = MainStatisticsResponse.builder()
                .totalRegisteredUsers(100)
                .dailyActiveUsers(25)
                .totalMatchSuccessCount(13)
                .generatedAt(LocalDateTime.now())
                .build();
        when(statisticsService.getMainStatistics()).thenReturn(main);
        when(ticketLedgerRepository.sumGrantedTickets()).thenReturn(200L);
        when(ticketLedgerRepository.sumConsumedTickets()).thenReturn(150L);
        when(userReportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(3L);

        AdminDtos.StatisticsOverview response = adminService.getStatisticsOverview();

        assertThat(response.totalRegisteredUsers()).isEqualTo(100);
        assertThat(response.grantedTickets()).isEqualTo(200L);
        assertThat(response.consumedTickets()).isEqualTo(150L);
        assertThat(response.openReports()).isEqualTo(3L);
    }

    private User user(Long id, int tickets) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("social-" + id)
                .email("u" + id + "@airconnect.test")
                .tickets(tickets)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
