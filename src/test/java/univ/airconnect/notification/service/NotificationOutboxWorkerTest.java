package univ.airconnect.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationOutboxService notificationOutboxService;
    @Mock
    private PushNotificationSender pushNotificationSender;
    @Mock
    private PushDeviceService pushDeviceService;
    @Mock
    private NotificationDeliveryGuard notificationDeliveryGuard;
    @Mock
    private AndroidPushSendGapService androidPushSendGapService;

    private NotificationOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new NotificationOutboxWorker(
                notificationOutboxService,
                pushNotificationSender,
                pushDeviceService,
                notificationDeliveryGuard,
                androidPushSendGapService
        );
    }

    @Test
    void drain_defersWhenGuardRequestsDelay() {
        NotificationOutbox outbox = outbox(0);
        LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 4, 25, 8, 0);

        when(notificationOutboxService.claimNextBatch(100)).thenReturn(List.of(outbox));
        when(notificationDeliveryGuard.evaluate(outbox))
                .thenReturn(GuardResult.defer(nextAttemptAt, "QUIET_HOURS", "Quiet hours are active."));

        worker.drain();

        verify(notificationOutboxService).markDeferred(outbox.getId(), "QUIET_HOURS", "Quiet hours are active.", nextAttemptAt);
        verify(pushNotificationSender, never()).send(any());
    }

    @Test
    void drain_schedulesRetryWithJitterInExpectedRange() {
        NotificationOutbox outbox = outbox(0);
        LocalDateTime before = LocalDateTime.now(java.time.Clock.systemUTC());

        when(notificationOutboxService.claimNextBatch(100)).thenReturn(List.of(outbox));
        when(notificationDeliveryGuard.evaluate(outbox)).thenReturn(GuardResult.sendNow());
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.retryableFailure("UNAVAILABLE", "temporary"));

        worker.drain();

        ArgumentCaptor<LocalDateTime> nextAttemptCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationOutboxService).markRetry(
                org.mockito.Mockito.eq(outbox.getId()),
                org.mockito.Mockito.eq("UNAVAILABLE"),
                org.mockito.Mockito.eq("temporary"),
                nextAttemptCaptor.capture()
        );

        LocalDateTime nextAttemptAt = nextAttemptCaptor.getValue();
        LocalDateTime min = before.plusMinutes(1);
        LocalDateTime max = before.plusMinutes(1).plusSeconds(30);

        assertThat(nextAttemptAt).isAfterOrEqualTo(min);
        assertThat(nextAttemptAt).isBeforeOrEqualTo(max.plusSeconds(2));
    }

    @Test
    void drain_marksFailedWhenRetryBudgetExhausted() {
        NotificationOutbox outbox = outbox(3);

        when(notificationOutboxService.claimNextBatch(100)).thenReturn(List.of(outbox));
        when(notificationDeliveryGuard.evaluate(outbox)).thenReturn(GuardResult.sendNow());
        when(pushNotificationSender.send(outbox))
                .thenReturn(PushNotificationSender.PushSendResult.retryableFailure("UNAVAILABLE", "temporary"));

        worker.drain();

        verify(notificationOutboxService).markFailed(outbox.getId(), "UNAVAILABLE", "temporary");
    }

    private NotificationOutbox outbox(int attemptCount) {
        NotificationOutbox outbox = NotificationOutbox.create(
                100L,
                1L,
                17L,
                PushProvider.FCM,
                "token-1",
                "title",
                "body",
                "{\"notificationType\":\"SYSTEM_ANNOUNCEMENT\"}",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", 77L);
        ReflectionTestUtils.setField(outbox, "attemptCount", attemptCount);
        return outbox;
    }
}
