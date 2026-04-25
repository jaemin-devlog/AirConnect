package univ.airconnect.notification.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<Notification> findByUserIdAndDedupeKey(Long userId, String dedupeKey);

    long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(Long userId);

    long countByUserIdAndTypeNotAndReadAtIsNullAndDeletedAtIsNull(Long userId, NotificationType excludedType);

    List<Notification> findByUserIdAndDeletedAtIsNullOrderByIdDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndReadAtIsNullAndDeletedAtIsNullOrderByIdDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndTypeNotAndDeletedAtIsNullOrderByIdDesc(
            Long userId,
            NotificationType excludedType,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeNotAndReadAtIsNullAndDeletedAtIsNullOrderByIdDesc(
            Long userId,
            NotificationType excludedType,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeAndDeletedAtIsNullOrderByIdDesc(
            Long userId,
            NotificationType type,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeAndReadAtIsNullAndDeletedAtIsNullOrderByIdDesc(
            Long userId,
            NotificationType type,
            Pageable pageable
    );

    List<Notification> findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            Long cursorId,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeNotAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            NotificationType excludedType,
            Long cursorId,
            Pageable pageable
    );

    List<Notification> findByUserIdAndReadAtIsNullAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            Long cursorId,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeNotAndReadAtIsNullAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            NotificationType excludedType,
            Long cursorId,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            NotificationType type,
            Long cursorId,
            Pageable pageable
    );

    List<Notification> findByUserIdAndTypeAndReadAtIsNullAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId,
            NotificationType type,
            Long cursorId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.readAt = :readAt
            where n.userId = :userId
              and n.readAt is null
              and n.deletedAt is null
              and n.type <> :excludedType
            """)
    int markAllReadExcludingType(
            @Param("userId") Long userId,
            @Param("excludedType") NotificationType excludedType,
            @Param("readAt") LocalDateTime readAt
    );
}
