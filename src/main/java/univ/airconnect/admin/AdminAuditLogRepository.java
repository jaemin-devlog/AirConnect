package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

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

    long countByActionAndMetadataJsonContaining(AdminAuditAction action, String text);
}
