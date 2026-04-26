package univ.airconnect.maintenance.dto.request;

import jakarta.validation.constraints.NotNull;

public record MaintenanceUpdateRequest(
        @NotNull Boolean enabled,
        String title,
        String message
) {
}
