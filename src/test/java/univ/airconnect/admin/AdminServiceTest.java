package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.domain.entity.AnalyticsEvent;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.entity.MatchingConnection;
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
    private IapOrderRepository iapOrderRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
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
                iapOrderRepository,
                ticketLedgerRepository,
                analyticsEventRepository,
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
        when(userRepository.findIdsByStatus(org.mockito.ArgumentMatchers.eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(10L, 20L), PageRequest.of(0, 500), 2));

        AdminDtos.NoticeBroadcastResult response = adminService.broadcastNotice(
                new AdminRequests.NoticeBroadcastRequest("공지", "점검 예정", "airconnect://notice", true)
        );

        verify(notificationService, times(2)).createAndEnqueue(any());
        assertThat(response.recipients()).isEqualTo(2);
    }

    @Test
    void getUserDetail_includesRecentHistories() {
        User user = user(5L, 10);
        User target = user(6L, 20);
        ReflectionTestUtils.setField(target, "nickname", "상대방");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userReportRepository.countByReportedUserIdAndStatus(5L, ReportStatus.OPEN)).thenReturn(2L);
        when(iapOrderRepository.findTop20ByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(iapOrder(5L)));
        when(matchingConnectionRepository.findTop20ByRequesterIdOrderByRecentDesc(5L)).thenReturn(List.of(connection(100L, 5L, 6L)));
        when(userRepository.findAllByIdWithProfile(java.util.Set.of(6L))).thenReturn(List.of(target));
        when(ticketLedgerRepository.findTop20UsageHistoryByUserId(5L)).thenReturn(List.of(ticketUsage(5L)));
        when(analyticsEventRepository.findTop20ByUserIdOrderByOccurredAtDescIdDesc(5L)).thenReturn(List.of(apiEvent(5L)));

        AdminDtos.UserDetail detail = adminService.getUserDetail(5L);

        assertThat(detail.openReportCount()).isEqualTo(2L);
        assertThat(detail.purchaseHistories()).hasSize(1);
        assertThat(detail.purchaseHistories().get(0).productId()).isEqualTo("ticket_10");
        assertThat(detail.sentRequestHistories()).hasSize(1);
        assertThat(detail.sentRequestHistories().get(0).targetNickname()).isEqualTo("상대방");
        assertThat(detail.ticketUsageHistories()).hasSize(1);
        assertThat(detail.ticketUsageHistories().get(0).usedAmount()).isEqualTo(2);
        assertThat(detail.apiUsageHistories()).hasSize(1);
        assertThat(detail.apiUsageHistories().get(0).type()).isEqualTo(AnalyticsEventType.USER_LOGGED_IN);
    }

    @Test
    void getStatisticsOverview_combinesExistingStatsAndTicketData() {
        MainStatisticsResponse main = MainStatisticsResponse.builder()
                .totalRegisteredUsers(100)
                .dailyActiveUsers(25)
                .genderRatio(MainStatisticsResponse.GenderRatio.builder()
                        .maleUsers(40)
                        .femaleUsers(50)
                        .unknownUsers(10)
                        .malePercentage(44)
                        .femalePercentage(56)
                        .build())
                .totalMatchSuccessCount(13)
                .topRequestedDepartments(List.of(
                        MainStatisticsResponse.DepartmentRanking.builder()
                                .rank(1)
                                .deptName("컴퓨터공학과")
                                .requestCount(12)
                                .build()
                ))
                .generatedAt(LocalDateTime.now())
                .build();
        when(statisticsService.getMainStatistics()).thenReturn(main);
        when(ticketLedgerRepository.sumGrantedTickets()).thenReturn(200L);
        when(ticketLedgerRepository.sumConsumedTickets()).thenReturn(150L);
        when(userReportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(3L);

        AdminDtos.StatisticsOverview response = adminService.getStatisticsOverview();

        assertThat(response.totalRegisteredUsers()).isEqualTo(100);
        assertThat(response.genderRatio()).isNotNull();
        assertThat(response.genderRatio().maleUsers()).isEqualTo(40);
        assertThat(response.topRequestedDepartments()).hasSize(1);
        assertThat(response.topRequestedDepartments().get(0).deptName()).isEqualTo("컴퓨터공학과");
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

    private IapOrder iapOrder(Long userId) {
        IapOrder order = IapOrder.createPending(
                userId,
                IapStore.APPLE,
                "ticket_10",
                "tx-1",
                "origin-tx-1",
                null,
                "order-1",
                "account-token",
                IapEnvironment.SANDBOX,
                "hash",
                "{}"
        );
        order.markGranted(10, 0, 10);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    private MatchingConnection connection(Long id, Long requesterId, Long targetUserId) {
        MatchingConnection connection = MatchingConnection.createPending(requesterId, targetUserId);
        ReflectionTestUtils.setField(connection, "id", id);
        return connection;
    }

    private TicketLedger ticketUsage(Long userId) {
        TicketLedger ledger = TicketLedger.consumeForMatchingConnect(userId, 2, 10, 8, "ref-1");
        ReflectionTestUtils.setField(ledger, "id", 11L);
        return ledger;
    }

    private AnalyticsEvent apiEvent(Long userId) {
        AnalyticsEvent event = AnalyticsEvent.server(
                userId,
                AnalyticsEventType.USER_LOGGED_IN,
                LocalDateTime.now(),
                "{\"provider\":\"APPLE\"}"
        );
        ReflectionTestUtils.setField(event, "id", 21L);
        return event;
    }
}
