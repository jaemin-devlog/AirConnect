package univ.airconnect.maintenance.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceContentUpdateRequest(
        @NotNull @Min(-1) Long expectedVersion,
        @Size(max = 120) String title,
        @Size(max = 500) String message
) {
}
