package univ.airconnect.maintenance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;

public interface MaintenanceSettingRepository extends JpaRepository<MaintenanceSetting, Long> {
}
