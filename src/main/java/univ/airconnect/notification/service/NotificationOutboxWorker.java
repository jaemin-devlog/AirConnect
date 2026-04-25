package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "notification.outbox.worker.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30)
    );
    private static final List<Duration> RETRY_JITTERS = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5)
    );
    private static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final NotificationOutboxService notificationOutboxService;
    private final PushNotificationSender pushNotificationSender;
    private final PushDeviceService pushDeviceService;
    private final NotificationDeliveryGuard notificationDeliveryGuard;
    private final AndroidPushSendGapService androidPushSendGapService;

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

    @Scheduled(fixedDelayString = "${notification.outbox.worker.recovery-delay-ms:60000}")
    public void recoverStuckClaims() {
        notificationOutboxService.recoverTimedOutProcessing(CLAIM_TIMEOUT);
    }

    private void dispatch(NotificationOutbox outbox) {
        try {
            GuardResult guardResult = notificationDeliveryGuard.evaluate(outbox);
            if (guardResult.decision() != DeliveryDecision.SEND_NOW) {
                handleGuardResult(outbox, guardResult);
                return;
            }

            PushNotificationSender.PushSendResult result = pushNotificationSender.send(outbox);
            if (result.success()) {
                notificationOutboxService.markSent(outbox.getId(), result.providerMessageId());
                androidPushSendGapService.recordSent(outbox, LocalDateTime.now(Clock.systemUTC()));
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

    private void handleGuardResult(NotificationOutbox outbox, GuardResult guardResult) {
        if (guardResult.decision() == DeliveryDecision.DEFER) {
            notificationOutboxService.markDeferred(
                    outbox.getId(),
                    guardResult.reason(),
                    guardResult.message(),
                    guardResult.nextAttemptAt()
            );
            return;
        }
        if (guardResult.decision() == DeliveryDecision.SKIP) {
            notificationOutboxService.markSkipped(outbox.getId(), guardResult.reason(), guardResult.message());
            return;
        }
        notificationOutboxService.markFailed(outbox.getId(), guardResult.reason(), guardResult.message());
    }

    private boolean canRetry(NotificationOutbox outbox) {
        return outbox.getAttemptCount() < RETRY_DELAYS.size()
                && outbox.getAttemptCount() + 1 < MAX_ATTEMPTS;
    }

    private LocalDateTime nextAttemptAt(NotificationOutbox outbox) {
        Duration delay = RETRY_DELAYS.get(outbox.getAttemptCount());
        Duration jitter = RETRY_JITTERS.get(outbox.getAttemptCount());
        return LocalDateTime.now(Clock.systemUTC()).plus(delay).plus(randomJitter(jitter));
    }

    private Duration randomJitter(Duration maxJitter) {
        if (maxJitter == null || maxJitter.isNegative() || maxJitter.isZero()) {
            return Duration.ZERO;
        }
        long maxMillis = maxJitter.toMillis();
        long jitterMillis = ThreadLocalRandom.current().nextLong(maxMillis + 1);
        return Duration.ofMillis(jitterMillis);
    }
}
