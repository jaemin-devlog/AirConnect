package univ.airconnect.moderation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;

@Getter
@NoArgsConstructor
public class CreateUserReportRequest {

    @NotNull(message = "신고 대상 사용자 ID는 필수입니다.")
    @Positive(message = "신고 대상 사용자 ID는 양수여야 합니다.")
    private Long reportedUserId;

    @NotNull(message = "신고 사유는 필수입니다.")
    private ReportReasonCode reportReason;

    @Size(max = 1000, message = "신고 상세 내용은 1000자 이하여야 합니다.")
    private String detail;

    private ReportSourceType sourceType;

    @Size(max = 120, message = "sourceId는 120자 이하여야 합니다.")
    private String sourceId;
}
