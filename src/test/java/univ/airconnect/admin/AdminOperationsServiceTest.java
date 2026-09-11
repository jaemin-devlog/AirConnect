package univ.airconnect.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.repository.ApiRequestLogRepository;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private ApiRequestLogRepository apiRequestLogRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserReportRepository userReportRepository;
    @Mock
    private GTemporaryTeamRoomRepository gTemporaryTeamRoomRepository;
    @Mock
    private GTemporaryTeamMemberRepository gTemporaryTeamMemberRepository;
    @Mock
    private GTeamReadyStateRepository gTeamReadyStateRepository;
    @Mock
    private GMatchResultRepository gMatchResultRepository;
    @Mock
    private GFinalGroupChatRoomRepository gFinalGroupChatRoomRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private AdminNoticeRepository adminNoticeRepository;
    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private AdminAuditLogService adminAuditLogService;
    @Mock
    private AdminIntegrityQueryRepository adminIntegrityQueryRepository;

    private AdminOperationsService adminOperationsService;

    @BeforeEach
    void setUp() {
        adminOperationsService = new AdminOperationsService(
                notificationOutboxRepository,
                notificationRepository,
                pushDeviceRepository,
                apiRequestLogRepository,
                analyticsEventRepository,
                matchingConnectionRepository,
                userRepository,
                chatMessageRepository,
                chatRoomRepository,
                chatRoomMemberRepository,
                userReportRepository,
                gTemporaryTeamRoomRepository,
                gTemporaryTeamMemberRepository,
                gTeamReadyStateRepository,
                gMatchResultRepository,
                gFinalGroupChatRoomRepository,
                ticketLedgerRepository,
                adminNoticeRepository,
                adminAuditLogRepository,
                adminAuditLogService,
                adminIntegrityQueryRepository
        );
    }

    @Test
    void getOutboxMonitor_returnsStatusCountsAndRecentFailures() {
        NotificationOutbox failed = NotificationOutbox.create(
                10L,
                20L,
                30L,
                PushProvider.FCM,
                "token",
                "title",
                "body",
                "{}",
                LocalDateTime.now()
        );
        failed.markFailed("FCM_ERROR", "send failed");
        ReflectionTestUtils.setField(failed, "id", 99L);

        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PENDING)).thenReturn(3L);
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PROCESSING)).thenReturn(1L);
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.SENT)).thenReturn(120L);
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.FAILED)).thenReturn(2L);
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.SKIPPED)).thenReturn(4L);
        when(notificationOutboxRepository.countByStatusAndNextAttemptAtBefore(any(), any())).thenReturn(1L);
        when(notificationOutboxRepository.countProcessingOlderThan(any(), any())).thenReturn(0L);
        when(notificationOutboxRepository.averageDeliverySecondsSince(any())).thenReturn(0.8);
        when(notificationOutboxRepository.findTop10ByStatusOrderByUpdatedAtDesc(NotificationDeliveryStatus.FAILED))
                .thenReturn(List.of(failed));

        AdminDtos.OutboxMonitor response = adminOperationsService.getOutboxMonitor(999L);

        assertThat(response.statusCounts()).hasSize(NotificationDeliveryStatus.values().length);
        assertThat(response.oldPendingCount()).isEqualTo(1L);
        assertThat(response.staleProcessingCount()).isZero();
        assertThat(response.averageDeliverySeconds()).isEqualTo(0.8);
        assertThat(response.recentFailures()).hasSize(1);
        assertThat(response.recentFailures().get(0).lastErrorCode()).isEqualTo("FCM_ERROR");
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getMatchingFunnel_returnsConversions() {
        when(analyticsEventRepository.countByTypeAndOccurredAtGreaterThanEqual(
                any(AnalyticsEventType.class),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> switch ((AnalyticsEventType) invocation.getArgument(0)) {
            case MATCH_RECOMMENDATION_REFRESHED -> 120L;
            case MATCH_REQUEST_SENT -> 30L;
            case MATCH_REQUEST_ACCEPTED -> 6L;
            default -> 0L;
        });
        when(matchingConnectionRepository.countByStatusAndChatRoomIdIsNotNullAndRespondedAtGreaterThanEqual(
                eq(ConnectionStatus.ACCEPTED),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(6L);
        when(matchingConnectionRepository.countByStatusInAndRespondedAtGreaterThanEqual(
                eq(List.of(ConnectionStatus.REJECTED, ConnectionStatus.CANCELLED, ConnectionStatus.EXPIRED)),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(4L);
        when(matchingConnectionRepository.averageResponseSecondsSince(any(LocalDateTime.class))).thenReturn(42.0);

        AdminDtos.MatchingFunnel response = adminOperationsService.getMatchingFunnel(999L, 30);

        assertThat(response.days()).isEqualTo(30);
        assertThat(response.requestCount()).isEqualTo(30L);
        assertThat(response.acceptedCount()).isEqualTo(6L);
        assertThat(response.rejectedOrExpiredCount()).isEqualTo(4L);
        assertThat(response.acceptanceRatePercentage()).isEqualTo(20);
        assertThat(response.averageResponseSeconds()).isEqualTo(42.0);
        assertThat(response.steps()).hasSize(5);
        assertThat(response.steps().get(1).conversionFromPreviousPercentage()).isEqualTo(25);
        assertThat(response.steps().get(2).conversionFromPreviousPercentage()).isEqualTo(20);
        assertThat(response.steps().get(4).conversionFromPreviousPercentage()).isEqualTo(100);
    }

    @Test
    void getApiUsageStatistics_returnsTotalAndTopEndpoint() {
        LocalDateTime now = LocalDateTime.now();
        when(apiRequestLogRepository.countByCreatedAtGreaterThanEqual(any(LocalDateTime.class))).thenReturn(240L);
        when(apiRequestLogRepository.countDistinctEndpointsSince(any(LocalDateTime.class))).thenReturn(18L);
        when(apiRequestLogRepository.findTopEndpointsSince(any(LocalDateTime.class), any()))
                .thenReturn(List.of(
                        projection("GET", "/api/v1/matchings/recommendations", 120L, 82.4, now.minusMinutes(3)),
                        projection("POST", "/api/v1/chat-rooms/{roomId}/messages", 75L, 45.0, now.minusMinutes(1))
                ));

        AdminDtos.ApiUsageStatistics response = adminOperationsService.getApiUsageStatistics(999L, 30);

        assertThat(response.totalApiCallCount()).isEqualTo(240L);
        assertThat(response.uniqueEndpointCount()).isEqualTo(18L);
        assertThat(response.mostCalledApi()).isNotNull();
        assertThat(response.mostCalledApi().method()).isEqualTo("GET");
        assertThat(response.mostCalledApi().path()).isEqualTo("/api/v1/matchings/recommendations");
        assertThat(response.topApiCalls()).hasSize(2);
        verify(adminAuditLogService).record(any(), eq(AdminAuditAction.API_USAGE_VIEWED), any(), any(), any(), any(), any());
    }

    @Test
    void getOperationsSummary_returnsCoreDashboardMetrics() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(9);
        univ.airconnect.user.domain.entity.User firstUser =
                univ.airconnect.user.domain.entity.User.builder()
                        .provider(univ.airconnect.auth.domain.entity.SocialProvider.APPLE)
                        .socialId("first")
                        .status(univ.airconnect.user.domain.UserStatus.ACTIVE)
                        .onboardingStatus(OnboardingStatus.FULL)
                        .tickets(10)
                        .createdAt(createdAt)
                        .build();

        when(userRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(firstUser));
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PENDING)).thenReturn(3L);
        when(notificationOutboxRepository.countByStatus(NotificationDeliveryStatus.PROCESSING)).thenReturn(2L);
        when(userReportRepository.countByStatusIn(List.of(ReportStatus.OPEN, ReportStatus.IN_REVIEW))).thenReturn(4L);
        when(userRepository.count()).thenReturn(20L);
        when(userRepository.countByOnboardingStatus(OnboardingStatus.FULL)).thenReturn(15L);
        when(userRepository.countByLastActiveAtGreaterThanEqual(any(LocalDateTime.class))).thenReturn(9L, 12L, 14L);
        when(matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED)).thenReturn(7L);
        when(chatMessageRepository.countByDeletedFalse()).thenReturn(50L);

        AdminDtos.OperationsSummary response = adminOperationsService.getOperationsSummary(999L);

        assertThat(response.totalRegisteredUsers()).isEqualTo(20L);
        assertThat(response.onboardingCompletedUsers()).isEqualTo(15L);
        assertThat(response.dailyActiveUsers()).isEqualTo(9L);
        assertThat(response.weeklyActiveUsers()).isEqualTo(12L);
        assertThat(response.monthlyActiveUsers()).isEqualTo(14L);
        assertThat(response.outboxBacklog()).isEqualTo(5L);
    }

    @Test
    void getIntegrityReport_marksFailuresWhenCountsExist() {
        when(chatRoomRepository.countRoomsWithoutVisibleMembers()).thenReturn(0L);
        when(chatRoomMemberRepository.countPersonalRoomsWithInvalidVisibleMemberCount()).thenReturn(1L);
        when(matchingConnectionRepository.countByStatusAndChatRoomIdIsNull(ConnectionStatus.ACCEPTED)).thenReturn(0L);
        when(matchingConnectionRepository.countAcceptedConnectionsWithMissingChatRoom()).thenReturn(0L);
        when(notificationOutboxRepository.countRowsMissingNotification()).thenReturn(0L);
        when(ticketLedgerRepository.countBrokenAmountRows()).thenReturn(2L);
        when(ticketLedgerRepository.countDuplicateRefRows()).thenReturn(0L);

        AdminDtos.IntegrityReport response = adminOperationsService.getIntegrityReport(999L);

        assertThat(response.checks()).hasSize(7);
        assertThat(response.failureCount()).isEqualTo(1L);
        assertThat(response.warningCount()).isEqualTo(1L);
        assertThat(response.checks())
                .extracting(AdminDtos.IntegrityCheckItem::status)
                .contains("PASS", "FAIL");
        assertThat(response.checks())
                .filteredOn(item -> item.key().startsWith("ticket_ledger_"))
                .extracting(AdminDtos.IntegrityCheckItem::label)
                .containsExactly("티켓 변동 내역 계산 불일치", "티켓 변동 내역 연결 정보 중복");
        assertThat(response.checks())
                .filteredOn(item -> item.key().startsWith("ticket_ledger_"))
                .extracting(AdminDtos.IntegrityCheckItem::description)
                .containsExactly(
                        "변경 전 잔액에 증감 수량을 더한 값이 변경 후 잔액과 다른 내역입니다.",
                        "같은 참조 유형과 참조 번호로 기록된 티켓 변동 내역이 여러 건 있습니다.");
    }

    @Test
    void getIntegrityIssues_requiresCurrentAdminAndReturnsRequestedPage() {
        User admin = User.builder().id(99L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        var expected = new AdminDtos.PageResponse<AdminDtos.IntegrityIssueItem>(
                List.of(), 1, 20, 0, 0, false);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(adminIntegrityQueryRepository.find("chat_rooms_without_members", 1, 20)).thenReturn(expected);

        var result = adminOperationsService.getIntegrityIssues(
                99L, "chat_rooms_without_members", 1, 20);

        assertThat(result).isSameAs(expected);
        verify(adminAuditLogService).record(eq(99L), eq(AdminAuditAction.INTEGRITY_ISSUES_VIEWED),
                eq("INTEGRITY_CHECK"), eq("chat_rooms_without_members"), any(), any(), any());
    }

    @Test
    void retryFailedOutbox_queuesOnlyForSameActiveDeviceAndAuditsInTransaction() {
        User admin = User.builder().id(99L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        NotificationOutbox outbox = NotificationOutbox.create(
                10L, 20L, 30L, PushProvider.FCM, "same-token", "title", "body", "{}", LocalDateTime.now());
        ReflectionTestUtils.setField(outbox, "id", 40L);
        outbox.markFailed("FCM_ERROR", "failed");
        PushDevice device = PushDevice.register(
                20L, "device", PushPlatform.ANDROID, PushProvider.FCM, "same-token", null,
                true, "1", "1", "ko", "Asia/Seoul", LocalDateTime.now());
        ReflectionTestUtils.setField(device, "id", 30L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(notificationOutboxRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.existsById(10L)).thenReturn(true);
        when(pushDeviceRepository.findByIdAndUserId(30L, 20L)).thenReturn(Optional.of(device));

        var result = adminOperationsService.retryFailedOutbox(99L, 40L);

        assertThat(result.queued()).isTrue();
        assertThat(result.status()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        verify(adminAuditLogService).recordOutboxRetry(eq(99L), any());
    }

    @Test
    void retryFailedOutbox_rejectsChangedDeviceWithoutQueuing() {
        User admin = User.builder().id(99L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        NotificationOutbox outbox = NotificationOutbox.create(
                10L, 20L, 30L, PushProvider.FCM, "old-token", "title", "body", "{}", LocalDateTime.now());
        ReflectionTestUtils.setField(outbox, "id", 40L);
        outbox.markFailed("FCM_ERROR", "failed");
        PushDevice device = PushDevice.register(
                20L, "device", PushPlatform.ANDROID, PushProvider.FCM, "new-token", null,
                true, "1", "1", "ko", "Asia/Seoul", LocalDateTime.now());
        ReflectionTestUtils.setField(device, "id", 30L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(notificationOutboxRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.existsById(10L)).thenReturn(true);
        when(pushDeviceRepository.findByIdAndUserId(30L, 20L)).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> adminOperationsService.retryFailedOutbox(99L, 40L))
                .hasMessageContaining("활성 기기");
        assertThat(outbox.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
    }

    private ApiRequestLogRepository.ApiRequestUsageProjection projection(
            String method,
            String path,
            long count,
            Double averageDurationMs,
            LocalDateTime lastCalledAt
    ) {
        return new ApiRequestLogRepository.ApiRequestUsageProjection() {
            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public long getCount() {
                return count;
            }

            @Override
            public Double getAverageDurationMs() {
                return averageDurationMs;
            }

            @Override
            public LocalDateTime getLastCalledAt() {
                return lastCalledAt;
            }
        };
    }
}
