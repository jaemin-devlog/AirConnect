package univ.airconnect.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.notification.domain.PushEventType;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.repository.PushEventRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PushEventServiceTest {
    @Mock PushEventRepository pushEventRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock PushDeviceRepository pushDeviceRepository;
    @InjectMocks PushEventService service;

    private PushEventService.CreateCommand command(String id) {
        return new PushEventService.CreateCommand(id, "fcm-message", PushEventType.values()[0],
                LocalDateTime.now(), "device-1");
    }

    @Test
    void savesEventForOwnedNotificationAndDevice() {
        when(notificationRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        when(pushDeviceRepository.findByUserIdAndDeviceIdForUpdate(1L, "device-1"))
                .thenReturn(Optional.of(mock(PushDevice.class)));
        when(pushEventRepository.save(any(PushEvent.class))).thenAnswer(i -> i.getArgument(0));
        PushEvent event = service.create(1L, command("10"));
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getNotificationId()).isEqualTo(10L);
        assertThat(event.getDeviceId()).isEqualTo("device-1");
    }

    @Test
    void rejectsForeignOrMissingNotificationWithoutSaving() {
        assertThatThrownBy(() -> service.create(1L, command("10")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(pushDeviceRepository, pushEventRepository);
    }

    @Test
    void rejectsForeignOrUnregisteredDeviceWithoutSaving() {
        when(notificationRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(1L, command("10")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(pushEventRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1", "abc", "999999999999999999999"})
    void rejectsInvalidNotificationId(String id) {
        assertThatThrownBy(() -> service.create(1L, command(id))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(notificationRepository, pushDeviceRepository, pushEventRepository);
    }
}
