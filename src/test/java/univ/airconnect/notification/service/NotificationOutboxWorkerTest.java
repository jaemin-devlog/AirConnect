package univ.airconnect.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxWorkerTest {

    @Mock
    private NotificationOutboxService notificationOutboxService;
    @Mock
    private NotificationOutboxDispatchService notificationOutboxDispatchService;

    @Test
    void drainRoutesEveryClaimedOutboxThroughRecipientRevalidation() {
        NotificationOutbox outbox = NotificationOutbox.create(
                1L,
                2L,
                3L,
                PushProvider.FCM,
                "token",
                "title",
                "body",
                "{}",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(outbox, "id", 4L);
        when(notificationOutboxService.claimNextBatch(100)).thenReturn(List.of(outbox));
        NotificationOutboxWorker worker = new NotificationOutboxWorker(
                notificationOutboxService,
                notificationOutboxDispatchService
        );

        worker.drain();

        verify(notificationOutboxDispatchService).dispatch(4L);
    }
}
