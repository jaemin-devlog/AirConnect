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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository,
                notificationOutboxRepository,
                notificationPreferenceService,
                pushDeviceService,
                new ObjectMapper()
        );
    }

    @Test
    void createAndEnqueue_coalescesPendingAndroidChatOutboxByRoom() {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        NotificationOutbox existingOutbox = NotificationOutbox.create(
                4001L,
                9L,
                17L,
                PushProvider.FCM,
                "old-token",
                "old-title",
                "old-body",
                "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"88\"}",
                LocalDateTime.of(2026, 4, 26, 10, 0, 1)
        );

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
        when(notificationOutboxRepository.findPendingChatOutboxForUpdate(17L, "88"))
                .thenReturn(Optional.of(existingOutbox));

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
        assertThat(existingOutbox.getDataJson()).contains("\"chatRoomId\":\"88\"");

        ArgumentCaptor<List<NotificationOutbox>> outboxesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationOutboxRepository).saveAll(outboxesCaptor.capture());
        assertThat(outboxesCaptor.getValue()).isEmpty();
    }

    @Test
    void createAndEnqueueCapsContinuousAndroidChatCoalescingAtFiveSecondsFromFirstOutbox() {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        NotificationOutbox existingOutbox = NotificationOutbox.create(
                4001L,
                9L,
                17L,
                PushProvider.FCM,
                "old-token",
                "old-title",
                "old-body",
                "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"88\"}",
                LocalDateTime.now().plusSeconds(2)
        );
        LocalDateTime firstOutboxCreatedAt = LocalDateTime.now().minusSeconds(4);
        ReflectionTestUtils.setField(existingOutbox, "createdAt", firstOutboxCreatedAt);
        AtomicLong notificationId = new AtomicLong(5000L);

        when(notificationPreferenceService.getDeliveryPolicy(9L, NotificationType.CHAT_MESSAGE_RECEIVED))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(notificationRepository.findByUserIdAndDedupeKey(eq(9L), anyString()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", notificationId.incrementAndGet());
            return notification;
        });
        when(pushDeviceService.findPushableDevices(9L)).thenReturn(List.of(androidDevice));
        when(notificationOutboxRepository.findPendingChatOutboxForUpdate(17L, "88"))
                .thenReturn(Optional.of(existingOutbox));

        for (int sequence = 1; sequence <= 30; sequence++) {
            service.createAndEnqueue(new NotificationService.CreateCommand(
                    9L,
                    NotificationType.CHAT_MESSAGE_RECEIVED,
                    "Minsu",
                    "Message " + sequence,
                    "airconnect://chat/rooms/88",
                    22L,
                    null,
                    "{\"chatRoomId\":\"88\",\"messageId\":\"" + sequence + "\"}",
                    "chat-message-" + sequence
            ));
        }

        assertThat(existingOutbox.getNextAttemptAt())
                .isBeforeOrEqualTo(firstOutboxCreatedAt.plus(NotificationService.ANDROID_CHAT_MAX_COALESCING_DELAY));
        assertThat(existingOutbox.getBody()).isEqualTo("Message 30");
    }

    @Test
    void createAndEnqueueCreatesOneChatOutboxPerPushableDevice() {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        PushDevice iosDevice = pushDevice(PushPlatform.IOS);

        when(notificationPreferenceService.getDeliveryPolicy(9L, NotificationType.CHAT_MESSAGE_RECEIVED))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(notificationRepository.findByUserIdAndDedupeKey(9L, "chat-multi-device"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 5002L);
            return notification;
        });
        when(pushDeviceService.findPushableDevices(9L)).thenReturn(List.of(androidDevice, iosDevice));
        when(notificationOutboxRepository.findPendingChatOutboxForUpdate(17L, "88"))
                .thenReturn(Optional.empty());

        service.createAndEnqueue(new NotificationService.CreateCommand(
                9L,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                "Minsu",
                "Hello both devices",
                "airconnect://chat/rooms/88",
                22L,
                null,
                "{\"chatRoomId\":\"88\",\"messageId\":\"9001\"}",
                "chat-multi-device"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationOutbox>> outboxesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationOutboxRepository).saveAll(outboxesCaptor.capture());
        assertThat(outboxesCaptor.getValue()).hasSize(2);
        assertThat(outboxesCaptor.getValue()).extracting(NotificationOutbox::getPushDeviceId)
                .containsExactlyInAnyOrder(17L, 18L);
    }

    @Test
    void createAndEnqueue_skipsAndroidReadyChangedPushButKeepsIos() {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        PushDevice iosDevice = pushDevice(PushPlatform.IOS);

        when(notificationPreferenceService.getDeliveryPolicy(9L, NotificationType.TEAM_MEMBER_READY_CHANGED))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 6001L);
            return notification;
        });
        when(pushDeviceService.findPushableDevices(9L)).thenReturn(List.of(androidDevice, iosDevice));

        service.createAndEnqueue(new NotificationService.CreateCommand(
                9L,
                NotificationType.TEAM_MEMBER_READY_CHANGED,
                "준비 상태 변경",
                "대기중인 팀원 한 명이 준비를 눌렀습니다.",
                "airconnect://matching/team-rooms/77",
                22L,
                null,
                "{\"teamRoomId\":\"77\",\"readyUserId\":\"22\"}",
                null
        ));

        ArgumentCaptor<List<NotificationOutbox>> outboxesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationOutboxRepository).saveAll(outboxesCaptor.capture());
        assertThat(outboxesCaptor.getValue()).hasSize(1);
        assertThat(outboxesCaptor.getValue().get(0).getPushDeviceId()).isEqualTo(iosDevice.getId());
    }

    private PushDevice pushDevice(PushPlatform platform) {
        PushDevice device = PushDevice.register(
                9L,
                "device-1",
                platform,
                PushProvider.FCM,
                "fcm-token",
                platform == PushPlatform.IOS ? "apns-token" : null,
                true,
                "1.0.0",
                "17",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(device, "id", platform == PushPlatform.IOS ? 18L : 17L);
        return device;
    }
}
