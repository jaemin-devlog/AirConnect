package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findByActionAndReportIdOrderByCreatedAtDescIdDesc(AdminAuditAction action, Long reportId);

    @Query("""
        SELECT l
        FROM AdminAuditLog l
        WHERE (:actorUserId IS NULL OR l.actorUserId = :actorUserId)
          AND (:action IS NULL OR l.action = :action)
          AND (:targetType IS NULL OR l.targetType = :targetType)
        ORDER BY l.createdAt DESC, l.id DESC
    """)
    Page<AdminAuditLog> search(@Param("actorUserId") Long actorUserId,
                               @Param("action") AdminAuditAction action,
                               @Param("targetType") String targetType,
                               Pageable pageable);

    long countByAction(AdminAuditAction action);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    long countByActionAndCreatedAtGreaterThanEqual(AdminAuditAction action, LocalDateTime since);

    long countByActionAndMetadataJsonContaining(AdminAuditAction action, String text);

    long deleteByActionAndCreatedAtBefore(AdminAuditAction action, LocalDateTime cutoff);

    @Query("""
        SELECT COUNT(DISTINCT l.actorUserId)
        FROM AdminAuditLog l
        WHERE l.createdAt >= :since
          AND l.actorUserId IS NOT NULL
    """)
    long countDistinctActorsSince(@Param("since") LocalDateTime since);

    @Query("""
        SELECT l.action AS action, COUNT(l) AS count
        FROM AdminAuditLog l
        WHERE l.createdAt >= :since
          AND l.action <> :excludedAction
        GROUP BY l.action
        ORDER BY COUNT(l) DESC
    """)
    List<ActionCountProjection> countActionsSince(@Param("since") LocalDateTime since,
                                                  @Param("excludedAction") AdminAuditAction excludedAction,
                                                  Pageable pageable);

    @Query("""
        SELECT l.apiMethod AS method,
               l.apiPath AS path,
               COUNT(l) AS count,
               AVG(l.durationMs) AS averageDurationMs
        FROM AdminAuditLog l
        WHERE l.createdAt >= :since
          AND l.apiPath IS NOT NULL
        GROUP BY l.apiMethod, l.apiPath
        ORDER BY COUNT(l) DESC
    """)
    List<ApiCountProjection> countApiCallsSince(@Param("since") LocalDateTime since, Pageable pageable);

    interface ActionCountProjection {
        AdminAuditAction getAction();

        long getCount();
    }

    interface ApiCountProjection {
        String getMethod();

        String getPath();

        long getCount();

        Double getAverageDurationMs();
    }
}
