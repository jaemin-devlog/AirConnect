package univ.airconnect.moderation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;

import java.time.LocalDateTime;
import java.util.List;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    List<UserReport> findTop50ByReporterUserIdOrderByCreatedAtDesc(Long reporterUserId);

    long countByReportedUserIdAndStatus(Long reportedUserId, ReportStatus status);

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
