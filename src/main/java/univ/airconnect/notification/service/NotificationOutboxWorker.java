package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox를 주기적으로 가져와 외부 푸시를 발송하는 워커다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "notification.outbox.worker.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final NotificationOutboxService notificationOutboxService;
    private final PushNotificationSender pushNotificationSender;
    private final PushDeviceService pushDeviceService;

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
            dispatch(outbox);
        }
    }

    /**
     * 장애나 프로세스 중단으로 고아가 된 PROCESSING 행을 복구한다.
     */
    @Scheduled(fixedDelayString = "${notification.outbox.worker.recovery-delay-ms:60000}")
    public void recoverStuckClaims() {
        notificationOutboxService.recoverTimedOutProcessing(CLAIM_TIMEOUT);
    }

    /**
     * 개별 outbox를 발송하고 결과에 따라 상태를 전이한다.
     */
    private void dispatch(NotificationOutbox outbox) {
        try {
            PushNotificationSender.PushSendResult result = pushNotificationSender.send(outbox);
            if (result.success()) {
                notificationOutboxService.markSent(outbox.getId(), result.providerMessageId());
                return;
            }

            if (result.invalidToken()) {
                pushDeviceService.deactivateInvalidToken(outbox.getProvider(), outbox.getTargetToken());
                notificationOutboxService.markSkipped(outbox.getId(), result.errorCode(), result.errorMessage());
                return;
            }

            if (result.retryable() && canRetry(outbox)) {
                notificationOutboxService.markRetry(
                        outbox.getId(),
                        result.errorCode(),
                        result.errorMessage(),
                        nextAttemptAt(outbox)
                );
                return;
            }

            notificationOutboxService.markFailed(outbox.getId(), result.errorCode(), result.errorMessage());
        } catch (Exception e) {
            log.error("Notification outbox dispatch failed unexpectedly: outboxId={}", outbox.getId(), e);
            if (canRetry(outbox)) {
                notificationOutboxService.markRetry(
                        outbox.getId(),
                        "UNEXPECTED_ERROR",
                        e.getMessage(),
                        nextAttemptAt(outbox)
                );
                return;
            }
            notificationOutboxService.markFailed(outbox.getId(), "UNEXPECTED_ERROR", e.getMessage());
        }
    }

    /**
     * 현재 시도 이후에도 재시도 여유가 있는지 계산한다.
     */
    private boolean canRetry(NotificationOutbox outbox) {
        return outbox.getAttemptCount() + 1 < MAX_ATTEMPTS;
    }

    /**
     * 지수적으로 길어지는 단순 재시도 시각을 계산한다.
     */
    private LocalDateTime nextAttemptAt(NotificationOutbox outbox) {
        int nextAttemptNumber = outbox.getAttemptCount() + 1;
        return switch (nextAttemptNumber) {
            case 1 -> LocalDateTime.now().plusMinutes(1);
            case 2 -> LocalDateTime.now().plusMinutes(5);
            default -> LocalDateTime.now().plusMinutes(30);
        };
    }
}
