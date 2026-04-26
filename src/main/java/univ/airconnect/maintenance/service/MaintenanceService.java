package univ.airconnect.maintenance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.repository.MaintenanceSettingRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceService {

    private final MaintenanceSettingRepository maintenanceSettingRepository;

    public MaintenanceStatusResponse getStatus() {
        return MaintenanceStatusResponse.from(getCurrentSetting());
    }

    public boolean isEnabled() {
        return getCurrentSetting().isEnabled();
    }

    public MaintenanceSetting getCurrentSetting() {
        return maintenanceSettingRepository.findById(MaintenanceSetting.SINGLETON_ID)
                .orElseGet(MaintenanceSetting::defaultValue);
    }

    @Transactional
    public MaintenanceStatusResponse updateStatus(Long adminUserId, boolean enabled, String title, String message) {
        MaintenanceSetting setting = maintenanceSettingRepository.findById(MaintenanceSetting.SINGLETON_ID)
                .orElseGet(MaintenanceSetting::defaultValue);
        setting.update(enabled, title, message, adminUserId);
        return MaintenanceStatusResponse.from(maintenanceSettingRepository.save(setting));
    }
}
