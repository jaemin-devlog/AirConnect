package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.Duration;
import java.util.List;

/**
 * outbox를 주기적으로 가져와 외부 푸시를 발송하는 워커다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "notification.outbox.worker.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final NotificationOutboxService notificationOutboxService;
    private final NotificationOutboxDispatchService notificationOutboxDispatchService;

    /**
     * 발송 가능한 outbox를 batch 단위로 처리한다.
     */
    @Scheduled(fixedDelayString = "${notification.outbox.worker.delay-ms:1000}")
    public void drain() {
        List<NotificationOutbox> batch = notificationOutboxService.claimNextBatch(DEFAULT_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        for (NotificationOutbox outbox : batch) {
            try {
                notificationOutboxDispatchService.dispatch(outbox.getId());
            } catch (RuntimeException e) {
                log.error("Notification outbox dispatch escaped item boundary: outboxId={}", outbox.getId(), e);
            }
        }
    }

    /**
     * 장애나 프로세스 중단으로 고아가 된 PROCESSING 행을 복구한다.
     */
    @Scheduled(fixedDelayString = "${notification.outbox.worker.recovery-delay-ms:60000}")
    public void recoverStuckClaims() {
        notificationOutboxService.recoverTimedOutProcessing(CLAIM_TIMEOUT);
    }

}
