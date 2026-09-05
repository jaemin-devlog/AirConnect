package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM NotificationOutbox o WHERE o.id = :id")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("id") Long id);

    List<NotificationOutbox> findByIdInOrderByIdAsc(Collection<Long> ids);

    long deleteByUserId(Long userId);

    long countByStatus(NotificationDeliveryStatus status);

    List<NotificationOutbox> findTop10ByStatusOrderByUpdatedAtDesc(NotificationDeliveryStatus status);

    @Query("""
            SELECT COUNT(o)
            FROM NotificationOutbox o
            WHERE o.status = :status
              AND o.nextAttemptAt < :threshold
            """)
    long countByStatusAndNextAttemptAtBefore(@Param("status") NotificationDeliveryStatus status,
                                             @Param("threshold") LocalDateTime threshold);

    @Query("""
            SELECT COUNT(o)
            FROM NotificationOutbox o
            WHERE o.status = :status
              AND o.claimedAt IS NOT NULL
              AND o.claimedAt < :threshold
            """)
    long countProcessingOlderThan(@Param("status") NotificationDeliveryStatus status,
                                  @Param("threshold") LocalDateTime threshold);

    @Query(value = """
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, created_at, sent_at)), 0)
            FROM notification_outbox
            WHERE status = 'SENT'
              AND sent_at IS NOT NULL
              AND created_at >= :since
            """, nativeQuery = true)
    Double averageDeliverySecondsSince(@Param("since") LocalDateTime since);

    @Query(value = """
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, created_at, updated_at)), 0)
            FROM notification_outbox
            WHERE status IN ('SENT', 'FAILED', 'SKIPPED')
              AND created_at >= :since
            """, nativeQuery = true)
    Double averageProcessingSecondsSince(@Param("since") LocalDateTime since);

    @Query(value = """
            SELECT COUNT(*)
            FROM notification_outbox
            WHERE (
                LOWER(COALESCE(last_error_code, '')) LIKE '%invalid%'
                OR LOWER(COALESCE(last_error_code, '')) LIKE '%unregistered%'
                OR LOWER(COALESCE(last_error_message, '')) LIKE '%invalid%token%'
                OR LOWER(COALESCE(last_error_message, '')) LIKE '%unregistered%'
            )
            """, nativeQuery = true)
    long countInvalidTokenFailures();

    @Query(value = """
            SELECT COALESCE(last_error_code, 'UNKNOWN') AS reason, COUNT(*) AS count
            FROM notification_outbox
            WHERE status = 'FAILED'
            GROUP BY COALESCE(last_error_code, 'UNKNOWN')
            ORDER BY COUNT(*) DESC, reason ASC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> countFailuresByReason();

    @Query(value = """
            SELECT COUNT(*)
            FROM notification_outbox o
            WHERE NOT EXISTS (
                SELECT 1
                FROM notifications n
                WHERE n.id = o.notification_id
            )
            """, nativeQuery = true)
    long countRowsMissingNotification();

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

    @Query(value = """
            SELECT *
            FROM notification_outbox no
            WHERE no.push_device_id = :pushDeviceId
              AND no.status = 'PENDING'
              AND JSON_UNQUOTE(JSON_EXTRACT(no.data_json, '$.notificationType')) = 'CHAT_MESSAGE_RECEIVED'
              AND JSON_UNQUOTE(JSON_EXTRACT(no.data_json, '$.chatRoomId')) = :chatRoomId
            ORDER BY no.id DESC
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<NotificationOutbox> findPendingChatOutboxForUpdate(@Param("pushDeviceId") Long pushDeviceId,
                                                                @Param("chatRoomId") String chatRoomId);
}
