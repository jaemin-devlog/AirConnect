package univ.airconnect.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "admin_audit_logs",
        indexes = {
                @Index(name = "idx_admin_audit_actor_created", columnList = "actor_user_id, created_at"),
                @Index(name = "idx_admin_audit_action_created", columnList = "action, created_at"),
                @Index(name = "idx_admin_audit_target_created", columnList = "target_type, target_id, created_at"),
                @Index(name = "idx_admin_audit_api_created", columnList = "api_path, created_at"),
                @Index(name = "idx_admin_audit_report_created", columnList = "report_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AdminAuditAction action;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 120)
    private String targetId;

    @Column(name = "report_id", updatable = false)
    private Long reportId;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(length = 500)
    private String reason;

    @Column(name = "metadata_json", columnDefinition = "JSON", nullable = false)
    private String metadataJson;

    @Column(name = "api_method", length = 12)
    private String apiMethod;

    @Column(name = "api_path", length = 200)
    private String apiPath;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminAuditLog(Long actorUserId,
                          AdminAuditAction action,
                          String targetType,
                          String targetId,
                          String summary,
                          String reason,
                          String metadataJson,
                          String apiMethod,
                          String apiPath,
                          Integer httpStatus,
                          Long durationMs) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = trimToNull(targetType);
        this.targetId = trimToNull(targetId);
        this.summary = requireText(summary, "summary");
        this.reason = trimToNull(reason);
        this.metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        this.apiMethod = trimToNull(apiMethod);
        this.apiPath = trimToNull(apiPath);
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }

    public static AdminAuditLog create(Long actorUserId,
                                       AdminAuditAction action,
                                       String targetType,
                                       String targetId,
                                       String summary,
                                       String reason,
                                       String metadataJson) {
        if (action == null) {
            throw new IllegalArgumentException("감사 로그 action은 필수입니다.");
        }
        return AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .summary(summary)
                .reason(reason)
                .metadataJson(metadataJson)
                .build();
    }

    public void linkToReport(Long reportId) {
        if (id != null || this.reportId != null || reportId == null || reportId <= 0) {
            throw new IllegalStateException("Only a new audit record can be linked to a report");
        }
        this.reportId = reportId;
    }

    public static AdminAuditLog createApiCall(Long actorUserId,
                                              String method,
                                              String path,
                                              Integer httpStatus,
                                              Long durationMs,
                                              String summary,
                                              String metadataJson) {
        return AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .action(AdminAuditAction.ADMIN_API_CALLED)
                .targetType("ADMIN_API")
                .targetId(trimToNull(method) + " " + trimToNull(path))
                .summary(summary)
                .metadataJson(metadataJson)
                .apiMethod(method)
                .apiPath(path)
                .httpStatus(httpStatus)
                .durationMs(durationMs)
                .build();
    }

    private static String requireText(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
