package univ.airconnect.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushEventType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.repository.PushEventRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushEventServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;
    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private PushEventRepository pushEventRepository;

    private PushEventService service;

    @BeforeEach
    void setUp() {
        service = new PushEventService(
                notificationRepository,
                notificationOutboxRepository,
                pushDeviceRepository,
                pushEventRepository
        );
    }

    @Test
    void create_rejectsEventForOtherUsersDevice() {
        Notification notification = notification();
        when(notificationRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(notification));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new PushEventService.CreateCommand(
                "100",
                null,
                PushEventType.OPENED,
                LocalDateTime.of(2026, 4, 25, 10, 0),
                "device-x"
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_returnsExistingDuplicateEvent() {
        PushEvent existing = PushEvent.create(
                1L,
                100L,
                null,
                PushEventType.OPENED,
                LocalDateTime.of(2026, 4, 25, 10, 0),
                "device-1"
        );
        when(notificationRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(notification()));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-1")).thenReturn(Optional.of(pushDevice()));
        when(pushEventRepository.findTopByUserIdAndNotificationIdAndEventTypeAndDeviceIdOrderByIdDesc(
                1L, 100L, PushEventType.OPENED, "device-1"
        )).thenReturn(Optional.of(existing));

        PushEvent response = service.create(1L, new PushEventService.CreateCommand(
                "100",
                null,
                PushEventType.OPENED,
                LocalDateTime.of(2026, 4, 25, 10, 0),
                "device-1"
        ));

        assertThat(response).isSameAs(existing);
    }

    @Test
    void create_allowsMissingProviderMessageIdWhenNotificationOutboxExistsForDevice() {
        Notification notification = notification();
        PushDevice pushDevice = pushDevice();
        NotificationOutbox outbox = outbox(pushDevice.getId());

        when(notificationRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(notification));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-1")).thenReturn(Optional.of(pushDevice));
        when(pushEventRepository.findTopByUserIdAndNotificationIdAndEventTypeAndDeviceIdOrderByIdDesc(
                1L, 100L, PushEventType.RECEIVED, "device-1"
        )).thenReturn(Optional.empty());
        when(notificationOutboxRepository.findTopByNotificationIdAndPushDeviceIdAndStatusOrderByIdDesc(
                100L,
                17L,
                NotificationDeliveryStatus.SENT
        ))
                .thenReturn(Optional.of(outbox));
        when(pushEventRepository.save(any(PushEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PushEvent response = service.create(1L, new PushEventService.CreateCommand(
                "100",
                null,
                PushEventType.RECEIVED,
                LocalDateTime.of(2026, 4, 25, 10, 5),
                "device-1"
        ));

        assertThat(response.getProviderMessageId()).isNull();
        verify(pushEventRepository).save(any(PushEvent.class));
    }

    @Test
    void create_rejectsEventWhenOnlyPendingOutboxExists() {
        Notification notification = notification();
        PushDevice pushDevice = pushDevice();

        when(notificationRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(notification));
        when(pushDeviceRepository.findByUserIdAndDeviceId(1L, "device-1")).thenReturn(Optional.of(pushDevice));
        when(pushEventRepository.findTopByUserIdAndNotificationIdAndEventTypeAndDeviceIdOrderByIdDesc(
                1L, 100L, PushEventType.OPENED, "device-1"
        )).thenReturn(Optional.empty());
        when(notificationOutboxRepository.findTopByNotificationIdAndPushDeviceIdAndStatusOrderByIdDesc(
                100L,
                17L,
                NotificationDeliveryStatus.SENT
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new PushEventService.CreateCommand(
                "100",
                null,
                PushEventType.OPENED,
                LocalDateTime.of(2026, 4, 25, 10, 10),
                "device-1"
        ))).isInstanceOf(BusinessException.class);
    }

    private Notification notification() {
        Notification notification = Notification.create(
                1L,
                univ.airconnect.notification.domain.NotificationType.SYSTEM_ANNOUNCEMENT,
                "공지",
                "본문",
                "/notifications/100",
                null,
                null,
                "{\"resourceId\":\"100\"}",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 100L);
        return notification;
    }

    private PushDevice pushDevice() {
        PushDevice device = PushDevice.register(
                1L,
                "device-1",
                PushPlatform.ANDROID,
                PushProvider.FCM,
                "token-1",
                null,
                true,
                "1.0.0",
                "14",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        );
        ReflectionTestUtils.setField(device, "id", 17L);
        return device;
    }

    private NotificationOutbox outbox(Long pushDeviceId) {
        NotificationOutbox outbox = NotificationOutbox.create(
                100L,
                1L,
                pushDeviceId,
                PushProvider.FCM,
                "token-1",
                "공지",
                "본문",
                "{\"notificationType\":\"SYSTEM_ANNOUNCEMENT\"}",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        );
        outbox.markSent("provider-123");
        ReflectionTestUtils.setField(outbox, "id", 999L);
        return outbox;
    }
}
