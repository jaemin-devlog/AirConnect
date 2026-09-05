package univ.airconnect.moderation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM UserReport r WHERE r.id = :id")
    Optional<UserReport> findByIdForUpdate(@Param("id") Long id);

    List<UserReport> findTop50ByReporterUserIdOrderByCreatedAtDesc(Long reporterUserId);

    @Query("""
        SELECT r
        FROM UserReport r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:reportedUserId IS NULL OR r.reportedUserId = :reportedUserId)
        ORDER BY r.createdAt DESC, r.id DESC
    """)
    Page<UserReport> searchForAdmin(@Param("status") ReportStatus status,
                                    @Param("reportedUserId") Long reportedUserId,
                                    Pageable pageable);

    long countByStatus(ReportStatus status);

    long countByStatusIn(Collection<ReportStatus> statuses);

    long countByReportedUserIdAndStatus(Long reportedUserId, ReportStatus status);

    @Query(value = """
        SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, created_at, COALESCE(completed_at, updated_at))), 0)
        FROM user_reports
        WHERE status IN ('RESOLVED', 'REJECTED')
    """, nativeQuery = true)
    Double averageProcessingSeconds();

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM UserReport r
        WHERE r.reporterUserId = :reporterUserId
          AND r.reportedUserId = :reportedUserId
          AND r.reason = :reason
          AND r.status = :status
          AND r.createdAt >= :after
          AND r.sourceType = :sourceType
          AND (
              (:sourceId IS NULL AND r.sourceId IS NULL)
              OR r.sourceId = :sourceId
          )
    """)
    boolean existsRecentDuplicate(@Param("reporterUserId") Long reporterUserId,
                                  @Param("reportedUserId") Long reportedUserId,
                                  @Param("reason") ReportReasonCode reason,
                                  @Param("sourceType") ReportSourceType sourceType,
                                  @Param("sourceId") String sourceId,
                                  @Param("status") ReportStatus status,
                                  @Param("after") LocalDateTime after);
}
