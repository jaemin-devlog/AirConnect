package univ.airconnect.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsServiceTest {

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private AdminAuditLogService adminAuditLogService;

    private AdminOperationsService adminOperationsService;

    @BeforeEach
    void setUp() {
        adminOperationsService = new AdminOperationsService(
                notificationOutboxRepository,
                analyticsEventRepository,
                matchingConnectionRepository,
                chatRoomRepository,
                chatRoomMemberRepository,
                ticketLedgerRepository,
                adminAuditLogService
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

        AdminDtos.MatchingFunnel response = adminOperationsService.getMatchingFunnel(999L, 30);

        assertThat(response.days()).isEqualTo(30);
        assertThat(response.steps()).hasSize(4);
        assertThat(response.steps().get(1).conversionFromPreviousPercentage()).isEqualTo(25);
        assertThat(response.steps().get(2).conversionFromPreviousPercentage()).isEqualTo(20);
        assertThat(response.steps().get(3).conversionFromPreviousPercentage()).isEqualTo(100);
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
        assertThat(response.failureCount()).isEqualTo(2L);
        assertThat(response.checks())
                .extracting(AdminDtos.IntegrityCheckItem::status)
                .contains("PASS", "FAIL");
    }
}
