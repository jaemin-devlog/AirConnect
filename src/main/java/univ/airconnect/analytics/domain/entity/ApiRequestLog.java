package univ.airconnect.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "api_request_logs",
        indexes = {
                @Index(name = "idx_api_request_created", columnList = "created_at"),
                @Index(name = "idx_api_request_path_created", columnList = "path, created_at"),
                @Index(name = "idx_api_request_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 12)
    private String method;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "trace_id", length = 120)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ApiRequestLog(Long userId,
                          String method,
                          String path,
                          Integer httpStatus,
                          Long durationMs,
                          String traceId) {
        this.userId = userId;
        this.method = requireText(method, "method");
        this.path = requireText(path, "path");
        this.httpStatus = httpStatus;
        this.durationMs = durationMs;
        this.traceId = trimToNull(traceId);
        this.createdAt = LocalDateTime.now();
    }

    public static ApiRequestLog create(Long userId,
                                       String method,
                                       String path,
                                       Integer httpStatus,
                                       Long durationMs,
                                       String traceId) {
        return ApiRequestLog.builder()
                .userId(userId)
                .method(method)
                .path(path)
                .httpStatus(httpStatus)
                .durationMs(durationMs)
                .traceId(traceId)
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
