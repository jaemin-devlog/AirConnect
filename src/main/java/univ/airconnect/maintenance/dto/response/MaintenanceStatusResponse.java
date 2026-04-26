package univ.airconnect.maintenance.dto.response;

import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;

import java.time.LocalDateTime;

public record MaintenanceStatusResponse(
        boolean enabled,
        String title,
        String message,
        LocalDateTime startedAt,
        Long updatedByUserId,
        LocalDateTime updatedAt
) {
    public static MaintenanceStatusResponse from(MaintenanceSetting setting) {
        return new MaintenanceStatusResponse(
                setting.isEnabled(),
                setting.getTitle(),
                setting.getMessage(),
                setting.getStartedAt(),
                setting.getUpdatedByUserId(),
                setting.getUpdatedAt()
        );
    }
}
