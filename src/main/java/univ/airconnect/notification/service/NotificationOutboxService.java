package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.repository.NotificationOutboxRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;

    @Transactional
    public List<NotificationOutbox> claimNextBatch(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        LocalDateTime now = nowUtc();
        List<Long> ids = notificationOutboxRepository.findClaimableIdsForUpdate(now, limit);
        if (ids.isEmpty()) {
            return List.of();
        }

        int updatedCount = notificationOutboxRepository.markProcessing(ids, now);
        if (updatedCount == 0) {
            return List.of();
        }

        return notificationOutboxRepository.findByIdInOrderByIdAsc(ids);
    }

    @Transactional
    public int recoverTimedOutProcessing(Duration timeout) {
        LocalDateTime now = nowUtc();
        LocalDateTime threshold = now.minus(timeout);
        int recovered = notificationOutboxRepository.recoverTimedOutProcessing(threshold, now);
        if (recovered > 0) {
            log.warn("Recovered stuck notification outboxes: count={}", recovered);
        }
        return recovered;
    }

    @Transactional
    public void markSent(Long outboxId, String providerMessageId) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markSent(providerMessageId);
    }

    @Transactional
    public void markRetry(Long outboxId, String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markRetry(errorCode, errorMessage, nextAttemptAt);
    }

    @Transactional
    public void markDeferred(Long outboxId, String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.defer(errorCode, errorMessage, nextAttemptAt);
    }

    @Transactional
    public void markFailed(Long outboxId, String errorCode, String errorMessage) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markFailed(errorCode, errorMessage);
    }

    @Transactional
    public void markSkipped(Long outboxId, String errorCode, String errorMessage) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markSkipped(errorCode, errorMessage);
    }

    private NotificationOutbox getRequired(Long outboxId) {
        return notificationOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Notification outbox not found: " + outboxId));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
