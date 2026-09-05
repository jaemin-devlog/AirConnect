package univ.airconnect.maintenance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.repository.MaintenanceSettingRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

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
    public MaintenanceStatusResponse updateContent(Long adminUserId, long expectedVersion, String title, String message) {
        if ((title != null && title.length() > 120) || (message != null && message.length() > 500)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "제목은 120자, 내용은 500자 이내로 입력해 주세요.");
        }
        MaintenanceSetting setting = requireVersion(expectedVersion);
        setting.updateContent(title, message, adminUserId);
        return save(setting);
    }

    @Transactional
    public MaintenanceStatusResponse changeState(Long adminUserId, long expectedVersion, boolean enabled) {
        MaintenanceSetting setting = requireVersion(expectedVersion);
        setting.changeState(enabled, adminUserId);
        return save(setting);
    }

    private MaintenanceSetting requireVersion(long expectedVersion) {
        MaintenanceSetting setting = maintenanceSettingRepository.findById(MaintenanceSetting.SINGLETON_ID)
                .orElseGet(MaintenanceSetting::defaultValue);
        long currentVersion = setting.getVersion() == null ? -1L : setting.getVersion();
        if (expectedVersion < -1 || expectedVersion != currentVersion) {
            throw new BusinessException(ErrorCode.MAINTENANCE_CONFLICT);
        }
        return setting;
    }

    private MaintenanceStatusResponse save(MaintenanceSetting setting) {
        boolean initialInsert = setting.getVersion() == null;
        try {
            // @Version adds the original version to UPDATE's WHERE clause. A null
            // version makes Spring Data persist (not merge) a missing singleton.
            // Flush here so both update races and first-insert races fail as 409.
            return MaintenanceStatusResponse.from(maintenanceSettingRepository.saveAndFlush(setting));
        } catch (OptimisticLockingFailureException conflict) {
            throw new BusinessException(ErrorCode.MAINTENANCE_CONFLICT);
        } catch (DataIntegrityViolationException failure) {
            // Only the singleton PK's first-insert collision is a version conflict.
            if (initialInsert && isDuplicateKey(failure)) {
                throw new BusinessException(ErrorCode.MAINTENANCE_CONFLICT);
            }
            throw failure;
        }
    }

    private boolean isDuplicateKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql
                    && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
                return true; // MySQL duplicate key or isolated H2 unique violation.
            }
        }
        return false;
    }
}
