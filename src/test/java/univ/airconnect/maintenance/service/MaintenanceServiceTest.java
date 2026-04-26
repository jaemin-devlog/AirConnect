package univ.airconnect.maintenance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.repository.MaintenanceSettingRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private MaintenanceSettingRepository maintenanceSettingRepository;

    @Test
    void updateStatus_enablesMaintenanceAndPersistsSingletonSetting() {
        MaintenanceService maintenanceService = new MaintenanceService(maintenanceSettingRepository);
        when(maintenanceSettingRepository.findById(MaintenanceSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        when(maintenanceSettingRepository.save(any(MaintenanceSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MaintenanceStatusResponse response = maintenanceService.updateStatus(
                999L,
                true,
                "긴급 점검",
                "서버 안정화를 위해 잠시 점검합니다."
        );

        ArgumentCaptor<MaintenanceSetting> captor = ArgumentCaptor.forClass(MaintenanceSetting.class);
        verify(maintenanceSettingRepository).save(captor.capture());

        assertThat(captor.getValue().getId()).isEqualTo(MaintenanceSetting.SINGLETON_ID);
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getUpdatedByUserId()).isEqualTo(999L);
        assertThat(captor.getValue().getStartedAt()).isNotNull();
        assertThat(response.enabled()).isTrue();
        assertThat(response.title()).isEqualTo("긴급 점검");
    }

    @Test
    void getStatus_returnsDefaultWhenNoSettingExistsYet() {
        MaintenanceService maintenanceService = new MaintenanceService(maintenanceSettingRepository);
        when(maintenanceSettingRepository.findById(MaintenanceSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        MaintenanceStatusResponse response = maintenanceService.getStatus();

        assertThat(response.enabled()).isFalse();
        assertThat(response.title()).isEqualTo("서버 점검 중");
        assertThat(response.message()).contains("잠시 후 다시 시도");
    }
}
