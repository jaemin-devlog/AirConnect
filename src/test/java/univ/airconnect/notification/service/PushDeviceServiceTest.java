package univ.airconnect.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    @Test
    void registerOrUpdate_registersNewPushDevice() {
        PushDeviceService service = new PushDeviceService(pushDeviceRepository);
        LocalDateTime lastSeenAt = LocalDateTime.of(2026, 4, 4, 10, 0);
        PushDeviceService.UpsertCommand command = new PushDeviceService.UpsertCommand(
                1L,
                "device-1",
                PushPlatform.IOS,
                PushProvider.FCM,
                "push-token-1",
                "apns-token-1",
                true,
                "1.0.0",
                "18.4",
                "ko-KR",
                "Asia/Seoul",
                lastSeenAt
        );

        when(pushDeviceRepository.findByProviderAndPushToken(PushProvider.FCM, "push-token-1"))
                .thenReturn(Optional.empty());
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-1"))
                .thenReturn(Optional.empty());
        when(pushDeviceRepository.save(any(PushDevice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PushDevice.class));

        PushDevice response = service.registerOrUpdate(command);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getDeviceId()).isEqualTo("device-1");
        assertThat(response.getPushToken()).isEqualTo("push-token-1");
        assertThat(response.getNotificationPermissionGranted()).isTrue();
        assertThat(response.getActive()).isTrue();
        verify(pushDeviceRepository).save(any(PushDevice.class));
    }

    @Test
    void updatePermission_updatesGrantedFlagAndLastSeenAt() {
        PushDeviceService service = new PushDeviceService(pushDeviceRepository);
        PushDevice device = PushDevice.register(
                7L,
                "device-7",
                PushPlatform.IOS,
                PushProvider.FCM,
                "push-token-7",
                "apns-token-7",
                false,
                "1.0.0",
                "18.4",
                "ko-KR",
                "Asia/Seoul",
                null
        );
        LocalDateTime lastSeenAt = LocalDateTime.of(2026, 4, 4, 11, 15);

        when(pushDeviceRepository.findByUserIdAndDeviceId(7L, "device-7"))
                .thenReturn(Optional.of(device));

        PushDevice response = service.updatePermission(7L, "device-7", true, lastSeenAt);

        assertThat(response.getNotificationPermissionGranted()).isTrue();
        assertThat(response.getLastSeenAt()).isEqualTo(lastSeenAt);
    }

    @Test
    void registerOrUpdate_rejectsPlatformMismatchForExistingDevice() {
        PushDeviceService service = new PushDeviceService(pushDeviceRepository);
        PushDevice existing = PushDevice.register(
                1L,
                "device-1",
                PushPlatform.ANDROID,
                PushProvider.FCM,
                "old-token",
                null,
                true,
                "1.0.0",
                "14",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.of(2026, 4, 4, 10, 0)
        );

        PushDeviceService.UpsertCommand command = new PushDeviceService.UpsertCommand(
                1L,
                "device-1",
                PushPlatform.IOS,
                PushProvider.FCM,
                "new-token",
                "apns-token",
                true,
                "1.0.0",
                "18.4",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.of(2026, 4, 4, 10, 5)
        );

        when(pushDeviceRepository.findByProviderAndPushToken(PushProvider.FCM, "new-token"))
                .thenReturn(Optional.empty());
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerOrUpdate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Platform mismatch for existing device.");
    }
}
