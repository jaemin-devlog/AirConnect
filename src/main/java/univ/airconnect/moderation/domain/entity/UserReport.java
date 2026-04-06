package univ.airconnect.moderation.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_reports",
        indexes = {
                @Index(name = "idx_user_reports_reporter_created", columnList = "reporter_user_id, created_at"),
                @Index(name = "idx_user_reports_reported_status", columnList = "reported_user_id, status, created_at"),
                @Index(name = "idx_user_reports_status_created", columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReasonCode reason;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ReportSourceType sourceType;

    @Column(name = "source_id", length = 120)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserReport(Long reporterUserId,
                       Long reportedUserId,
                       ReportReasonCode reason,
                       String detail,
                       ReportSourceType sourceType,
                       String sourceId,
                       ReportStatus status) {
        this.reporterUserId = reporterUserId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.detail = detail;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static UserReport createReceived(Long reporterUserId,
                                            Long reportedUserId,
                                            ReportReasonCode reason,
                                            String detail,
                                            ReportSourceType sourceType,
                                            String sourceId) {
        return UserReport.builder()
                .reporterUserId(reporterUserId)
                .reportedUserId(reportedUserId)
                .reason(reason)
                .detail(detail)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .status(ReportStatus.OPEN)
                .build();
    }

    public void updateStatus(ReportStatus nextStatus) {
        this.status = nextStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
