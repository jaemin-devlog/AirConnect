package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findByIdInOrderByIdAsc(Collection<Long> ids);

    @Query("""
            SELECT outbox
            FROM NotificationOutbox outbox
            WHERE outbox.pushDeviceId = :pushDeviceId
              AND outbox.status = :status
              AND outbox.attemptCount = :attemptCount
              AND outbox.nextAttemptAt BETWEEN :from AND :to
            ORDER BY outbox.id ASC
            """)
    List<NotificationOutbox> findPendingCandidatesForCoalescing(@Param("pushDeviceId") Long pushDeviceId,
                                                                @Param("status") NotificationDeliveryStatus status,
                                                                @Param("attemptCount") Integer attemptCount,
                                                                @Param("from") LocalDateTime from,
                                                                @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT no.id
            FROM notification_outbox no
            WHERE no.status = 'PENDING'
              AND no.next_attempt_at <= :now
            ORDER BY no.next_attempt_at ASC, no.id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIdsForUpdate(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'PROCESSING',
                claimed_at = :claimedAt,
                updated_at = :claimedAt
            WHERE id IN (:ids)
              AND status = 'PENDING'
            """, nativeQuery = true)
    int markProcessing(@Param("ids") Collection<Long> ids, @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE notification_outbox
            SET status = 'PENDING',
                claimed_at = NULL,
                next_attempt_at = :recoveredAt,
                updated_at = :recoveredAt
            WHERE status = 'PROCESSING'
              AND claimed_at IS NOT NULL
              AND claimed_at < :threshold
            """, nativeQuery = true)
    int recoverTimedOutProcessing(@Param("threshold") LocalDateTime threshold,
                                  @Param("recoveredAt") LocalDateTime recoveredAt);
}
