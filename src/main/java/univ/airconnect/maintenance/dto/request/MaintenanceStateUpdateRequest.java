package univ.airconnect.maintenance.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MaintenanceStateUpdateRequest(
        @NotNull @Min(-1) Long expectedVersion,
        @NotNull Boolean enabled
) {
}
