package univ.airconnect.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import univ.airconnect.moderation.domain.ReportStatus;

import java.time.LocalDateTime;

public final class AdminRequests {

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
            LocalDateTime until
    ) {
    }

    public record ReportStatusUpdateRequest(
            @NotNull ReportStatus status
    ) {
    }

    public record TicketAdjustmentRequest(
            @NotNull @Positive Long userId,
            int amount,
            @NotBlank String reason
    ) {
    }

    public record NoticeBroadcastRequest(
            @NotBlank String title,
            @NotBlank String body,
            String deeplink,
            Boolean activeUsersOnly
    ) {
    }
}
