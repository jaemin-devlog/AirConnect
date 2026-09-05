package univ.airconnect.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import univ.airconnect.moderation.domain.ReportStatus;

import java.time.LocalDateTime;

public final class AdminRequests {

    // ticket_ledger.reason(60) also stores the server's six-character ADMIN: prefix.
    public static final int MAX_TICKET_ADJUSTMENT_REASON_LENGTH = 54;
    public static final String OPERATION_UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private AdminRequests() {
    }

    public enum UserActionType {
        SUSPEND,
        DELETE,
        REACTIVATE,
        RESTRICT_MATCHING,
        CLEAR_MATCHING_RESTRICTION
    }

    public record UserActionRequest(
            @NotNull UserActionType action,
            String reason,
            LocalDateTime until,
            @Positive Long reportId
    ) {
        public UserActionRequest(UserActionType action, String reason, LocalDateTime until) {
            this(action, reason, until, null);
        }
    }

    public record ReportStatusUpdateRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull ReportStatus status,
            @Size(max = 1000) String internalMemo,
            @Size(max = 300) String reporterReply
    ) {
        public ReportStatusUpdateRequest {
            internalMemo = normalizeReportText(internalMemo);
            reporterReply = normalizeReportText(reporterReply);
        }
    }

    public record ReportEvidenceInspectionRequest(
            @NotNull @Positive Long roomId,
            @NotNull ChatInspectionReason reason,
            LocalDateTime from,
            LocalDateTime to,
            Integer page,
            Integer size
    ) {
    }

    private static String normalizeReportText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public record TicketAdjustmentRequest(
            @NotBlank @Pattern(regexp = OPERATION_UUID_PATTERN) String operationId,
            @NotNull @Positive Long userId,
            int amount,
            @NotBlank @Size(max = MAX_TICKET_ADJUSTMENT_REASON_LENGTH) String reason
    ) {
        public TicketAdjustmentRequest {
            operationId = operationId == null ? null : operationId.trim();
            reason = reason == null ? null : reason.trim();
        }
    }

    public record NoticeBroadcastRequest(
            @NotBlank String title,
            @NotBlank String body,
            String deeplink,
            Boolean activeUsersOnly
    ) {
    }

    public enum ChatInspectionReason {
        REPORT_REVIEW,
        USER_SUPPORT,
        DELIVERY_INCIDENT
    }

    public record ChatMessageInspectionRequest(
            @NotNull ChatInspectionReason reason,
            LocalDateTime from,
            LocalDateTime to,
            Integer page,
            Integer size
    ) {
    }
}
