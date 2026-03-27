package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.repository.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox 행의 claim, 복구, 상태 전이를 담당하는 서비스다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationOutboxService {

    private final NotificationOutboxRepository notificationOutboxRepository;

    /**
     * 발송 가능한 outbox batch를 현재 트랜잭션에서 점유한다.
     *
     * <p>findClaimableIdsForUpdate와 markProcessing은 반드시 같은 트랜잭션 안에서 실행된다.</p>
     */
    @Transactional
    public List<NotificationOutbox> claimNextBatch(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
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

    /**
     * PROCESSING 상태로 오래 묶여 있는 행을 다시 PENDING 으로 되돌린다.
     */
    @Transactional
    public int recoverTimedOutProcessing(Duration timeout) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minus(timeout);
        int recovered = notificationOutboxRepository.recoverTimedOutProcessing(threshold, now);
        if (recovered > 0) {
            log.warn("Recovered stuck notification outboxes: count={}", recovered);
        }
        return recovered;
    }

    /**
     * 발송 성공 상태를 저장한다.
     */
    @Transactional
    public void markSent(Long outboxId, String providerMessageId) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markSent(providerMessageId);
    }

    /**
     * 재시도 가능한 실패를 저장한다.
     */
    @Transactional
    public void markRetry(Long outboxId, String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markRetry(errorCode, errorMessage, nextAttemptAt);
    }

    /**
     * 최종 실패 상태를 저장한다.
     */
    @Transactional
    public void markFailed(Long outboxId, String errorCode, String errorMessage) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markFailed(errorCode, errorMessage);
    }

    /**
     * 정책상 발송 제외 상태를 저장한다.
     */
    @Transactional
    public void markSkipped(Long outboxId, String errorCode, String errorMessage) {
        NotificationOutbox outbox = getRequired(outboxId);
        outbox.markSkipped(errorCode, errorMessage);
    }

    /**
     * outbox ID로 행을 반드시 조회한다.
     */
    private NotificationOutbox getRequired(Long outboxId) {
        return notificationOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("notification outbox not found: " + outboxId));
    }
}
