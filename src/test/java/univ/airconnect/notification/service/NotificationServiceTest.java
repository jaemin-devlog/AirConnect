package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @Mock
    private PushDeviceService pushDeviceService;

    @Mock
    private AndroidChatPushCoalescingService androidChatPushCoalescingService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository,
                notificationOutboxRepository,
                notificationPreferenceService,
                pushDeviceService,
                androidChatPushCoalescingService,
                new ObjectMapper()
        );
    }

    @Test
    void createAndEnqueue_updatesExistingPendingAndroidChatOutbox() {
        PushDevice androidDevice = pushDevice();
        NotificationOutbox existingOutbox = NotificationOutbox.create(
                4001L,
                9L,
                17L,
                PushProvider.FCM,
                "old-token",
                "old-title",
                "old-body",
                "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"88\"}",
                LocalDateTime.of(2026, 4, 25, 11, 0, 1)
        );
        ReflectionTestUtils.setField(existingOutbox, "id", 70L);

        when(notificationPreferenceService.getDeliveryPolicy(9L, NotificationType.CHAT_MESSAGE_RECEIVED))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(notificationRepository.findByUserIdAndDedupeKey(9L, "chat-5512"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 5001L);
            return notification;
        });
        when(pushDeviceService.findPushableDevices(9L)).thenReturn(List.of(androidDevice));
        when(androidChatPushCoalescingService.decide(
                eq(androidDevice),
                eq(NotificationType.CHAT_MESSAGE_RECEIVED),
                any(String.class),
                any(LocalDateTime.class)))
                .thenReturn(new AndroidChatPushCoalescingService.Decision(
                        LocalDateTime.of(2026, 4, 25, 11, 0, 2),
                        "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"88\",\"batchedMessageCount\":2}",
                        Optional.of(existingOutbox),
                        true
                ));

        service.createAndEnqueue(new NotificationService.CreateCommand(
                9L,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                "Minsu",
                "Hello there",
                "airconnect://chat/rooms/88",
                22L,
                null,
                "{\"chatRoomId\":\"88\",\"messageId\":\"5512\",\"senderNickname\":\"Minsu\",\"messagePreview\":\"Hello there\"}",
                "chat-5512"
        ));

        assertThat(existingOutbox.getNotificationId()).isEqualTo(5001L);
        assertThat(existingOutbox.getTargetToken()).isEqualTo("fcm-token");
        assertThat(existingOutbox.getTitle()).isEqualTo("Minsu");
        assertThat(existingOutbox.getBody()).isEqualTo("Hello there");
        assertThat(existingOutbox.getDataJson()).contains("\"batchedMessageCount\":2");
        assertThat(existingOutbox.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 4, 25, 11, 0, 2));

        ArgumentCaptor<List<NotificationOutbox>> outboxesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationOutboxRepository).saveAll(outboxesCaptor.capture());
        assertThat(outboxesCaptor.getValue()).isEmpty();
    }

    private PushDevice pushDevice() {
        PushDevice device = PushDevice.register(
                9L,
                "device-1",
                PushPlatform.ANDROID,
                PushProvider.FCM,
                "fcm-token",
                null,
                true,
                "1.0.0",
                "17",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(device, "id", 17L);
        return device;
    }
}
