package univ.airconnect.moderation.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class UserReportResponse {

    private Long reportId;
    private Long reporterUserId;
    private Long reportedUserId;
    private ReportReasonCode reportReason;
    private String detail;
    private ReportSourceType sourceType;
    private String sourceId;
    private ReportStatus status;
    private OffsetDateTime createdAt;

    public static UserReportResponse from(UserReport report) {
        return UserReportResponse.builder()
                .reportId(report.getId())
                .reporterUserId(report.getReporterUserId())
                .reportedUserId(report.getReportedUserId())
                .reportReason(report.getReason())
                .detail(report.getDetail())
                .sourceType(report.getSourceType())
                .sourceId(report.getSourceId())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt().atOffset(ZoneOffset.UTC))
                .build();
    }
}
