package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AndroidChatPushCoalescingServiceTest {

    private static final Long PUSH_DEVICE_ID = 31L;

    @Mock
    private NotificationOutboxRepository notificationOutboxRepository;

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    private ObjectMapper objectMapper;
    private AndroidChatPushCoalescingService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AndroidChatPushCoalescingService(notificationOutboxRepository, pushDeviceRepository, objectMapper);
    }

    @Test
    void decide_returnsNotCandidateForIosChat() {
        PushDevice iosDevice = pushDevice(PushPlatform.IOS);
        AndroidChatPushCoalescingService.Decision decision = service.decide(
                iosDevice,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                chatPayload("1041", "5512"),
                LocalDateTime.of(2026, 4, 25, 10, 0),
                LocalDateTime.of(2026, 4, 25, 10, 0)
        );

        assertThat(decision.candidate()).isFalse();
        assertThat(decision.payloadJson()).isEqualTo(chatPayload("1041", "5512"));
        verify(pushDeviceRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void decide_initializesBatchMetadataForAndroidChat() throws Exception {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        when(pushDeviceRepository.findByIdForUpdate(PUSH_DEVICE_ID)).thenReturn(Optional.of(androidDevice));
        when(notificationOutboxRepository.findPendingCandidatesForCoalescing(
                eq(PUSH_DEVICE_ID),
                eq(NotificationDeliveryStatus.PENDING),
                eq(0),
                any(),
                any()))
                .thenReturn(List.of());

        LocalDateTime now = LocalDateTime.of(2026, 4, 25, 10, 0);
        AndroidChatPushCoalescingService.Decision decision = service.decide(
                androidDevice,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                chatPayload("1041", "5512"),
                now,
                now
        );

        JsonNode payload = objectMapper.readTree(decision.payloadJson());
        assertThat(decision.candidate()).isTrue();
        assertThat(decision.existingPendingOutbox()).isEmpty();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plus(AndroidChatPushCoalescingService.COALESCING_WINDOW));
        assertThat(payload.path("batchedMessageCount").asInt()).isEqualTo(1);
        assertThat(payload.path("batchedWindowStartedAt").asText()).isEqualTo(now.toString());
    }

    @Test
    void decide_mergesIntoLatestPendingOutbox() throws Exception {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        NotificationOutbox existingOutbox = NotificationOutbox.create(
                1041L,
                17L,
                PUSH_DEVICE_ID,
                PushProvider.FCM,
                "fcm-token",
                "old-title",
                "old-body",
                """
                        {
                          "notificationId": "1041",
                          "type": "CHAT_MESSAGE",
                          "notificationType": "CHAT_MESSAGE_RECEIVED",
                          "title": "old-title",
                          "body": "old-body",
                          "chatRoomId": "88",
                          "messageId": "5511",
                          "batchedMessageCount": 2,
                          "batchedWindowStartedAt": "2026-04-25T09:59:59"
                        }
                        """,
                LocalDateTime.of(2026, 4, 25, 10, 0, 1)
        );
        ReflectionTestUtils.setField(existingOutbox, "id", 99L);

        when(pushDeviceRepository.findByIdForUpdate(PUSH_DEVICE_ID)).thenReturn(Optional.of(androidDevice));
        when(notificationOutboxRepository.findPendingCandidatesForCoalescing(
                eq(PUSH_DEVICE_ID),
                eq(NotificationDeliveryStatus.PENDING),
                eq(0),
                any(),
                any()))
                .thenReturn(List.of(existingOutbox));

        AndroidChatPushCoalescingService.Decision decision = service.decide(
                androidDevice,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                chatPayload("1042", "5512"),
                LocalDateTime.of(2026, 4, 25, 10, 0, 2),
                LocalDateTime.of(2026, 4, 25, 10, 0, 2)
        );

        JsonNode payload = objectMapper.readTree(decision.payloadJson());
        assertThat(decision.candidate()).isTrue();
        assertThat(decision.existingPendingOutbox()).contains(existingOutbox);
        assertThat(payload.path("messageId").asText()).isEqualTo("5512");
        assertThat(payload.path("batchedMessageCount").asInt()).isEqualTo(3);
        assertThat(payload.path("batchedWindowStartedAt").asText()).isEqualTo("2026-04-25T09:59:59");
    }

    @Test
    void decide_usesDeferredDeliveryAnchorForQuietHoursCoalescing() {
        PushDevice androidDevice = pushDevice(PushPlatform.ANDROID);
        NotificationOutbox deferredOutbox = NotificationOutbox.create(
                2041L,
                17L,
                PUSH_DEVICE_ID,
                PushProvider.FCM,
                "fcm-token",
                "old-title",
                "old-body",
                chatPayload("2041", "7711"),
                LocalDateTime.of(2026, 4, 25, 8, 0)
        );
        ReflectionTestUtils.setField(deferredOutbox, "id", 109L);

        when(pushDeviceRepository.findByIdForUpdate(PUSH_DEVICE_ID)).thenReturn(Optional.of(androidDevice));
        when(notificationOutboxRepository.findPendingCandidatesForCoalescing(
                eq(PUSH_DEVICE_ID),
                eq(NotificationDeliveryStatus.PENDING),
                eq(0),
                eq(LocalDateTime.of(2026, 4, 25, 8, 0)),
                eq(LocalDateTime.of(2026, 4, 25, 8, 0).plus(AndroidChatPushCoalescingService.COALESCING_WINDOW))))
                .thenReturn(List.of(deferredOutbox));

        AndroidChatPushCoalescingService.Decision decision = service.decide(
                androidDevice,
                NotificationType.CHAT_MESSAGE_RECEIVED,
                chatPayload("2042", "7712"),
                LocalDateTime.of(2026, 4, 25, 1, 0),
                LocalDateTime.of(2026, 4, 25, 8, 0)
        );

        assertThat(decision.existingPendingOutbox()).contains(deferredOutbox);
    }

    private PushDevice pushDevice(PushPlatform platform) {
        PushDevice device = PushDevice.register(
                17L,
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
        ReflectionTestUtils.setField(device, "id", PUSH_DEVICE_ID);
        return device;
    }

    private String chatPayload(String notificationId, String messageId) {
        return """
                {
                  "notificationId": "%s",
                  "type": "CHAT_MESSAGE",
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "title": "Minsu",
                  "body": "Hello there",
                  "chatRoomId": "88",
                  "messageId": "%s",
                  "senderNickname": "Minsu",
                  "messagePreview": "Hello there",
                  "deeplink": "airconnect://chat/rooms/88",
                  "sentAt": "2026-04-25T10:00:00"
                }
                """.formatted(notificationId, messageId);
    }
}
