package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationSenderTest {

    private static final Long PUSH_DEVICE_ID = 17L;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    private FirebasePushNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper(), pushDeviceRepository);
    }

    @Test
    void send_sendsAndroidChatAsDataOnly() throws Exception {
        Message message = sendAndCapture(chatOutbox(), PushPlatform.ANDROID);

        assertThat(ReflectionTestUtils.getField(message, "notification")).isNull();
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNull();

        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("high");
        assertThat(ReflectionTestUtils.getField(androidConfig, "notification")).isNull();

        Map<String, String> data = readData(message);
        assertThat(data)
                .containsEntry("notificationType", "CHAT_MESSAGE_RECEIVED")
                .containsEntry("type", "CHAT_MESSAGE")
                .containsEntry("chatRoomId", "88")
                .containsEntry("messageId", "5512")
                .containsEntry("senderNickname", "민수")
                .containsEntry("messagePreview", "오늘 시간 괜찮아요?")
                .containsEntry("messageType", "TEXT")
                .containsEntry("senderUserId", "22");
    }

    @Test
    void send_keepsIosChatOnNotificationAndApnsPath() throws Exception {
        Message message = sendAndCapture(chatOutbox(), PushPlatform.IOS);

        Object notification = ReflectionTestUtils.getField(message, "notification");
        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("민수");
        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("오늘 시간 괜찮아요?");
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNotNull();

        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(androidNotification).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isEqualTo("airconnect_chat_push");
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isEqualTo("chat-88");
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isEqualTo("PRIORITY_DEFAULT");
    }

    @Test
    void send_keepsAndroidNonChatOnExistingNotificationPath() throws Exception {
        Message message = sendAndCapture(matchRequestOutbox(), PushPlatform.ANDROID);

        Object notification = ReflectionTestUtils.getField(message, "notification");
        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("새 매칭 요청");
        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("지민님이 매칭을 요청했어요.");

        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("high");
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(androidNotification).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isNull();
    }

    private Message sendAndCapture(NotificationOutbox outbox, PushPlatform platform) throws Exception {
        PushDevice pushDevice = PushDevice.register(
                outbox.getUserId(),
                "device-1",
                platform,
                PushProvider.FCM,
                outbox.getTargetToken(),
                platform == PushPlatform.IOS ? "apns-token" : null,
                true,
                "1.0.0",
                "17.0",
                "ko-KR",
                "Asia/Seoul",
                LocalDateTime.now()
        );

        when(pushDeviceRepository.findById(PUSH_DEVICE_ID)).thenReturn(Optional.of(pushDevice));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("provider-message-id");

        sender.send(outbox);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readData(Message message) {
        return (Map<String, String>) ReflectionTestUtils.getField(message, "data");
    }

    private NotificationOutbox chatOutbox() {
        return NotificationOutbox.create(
                1041L,
                31L,
                PUSH_DEVICE_ID,
                PushProvider.FCM,
                "fcm-token",
                "민수",
                "오늘 시간 괜찮아요?",
                """
                        {
                          "notificationId": "1041",
                          "type": "CHAT_MESSAGE",
                          "notificationType": "CHAT_MESSAGE_RECEIVED",
                          "title": "민수",
                          "body": "오늘 시간 괜찮아요?",
                          "chatRoomId": "88",
                          "messageId": "5512",
                          "senderNickname": "민수",
                          "messagePreview": "오늘 시간 괜찮아요?",
                          "deeplink": "airconnect://chat/rooms/88",
                          "sentAt": "2026-04-24T10:15:00.123456",
                          "messageType": "TEXT",
                          "senderUserId": "22"
                        }
                        """,
                LocalDateTime.of(2026, 4, 24, 10, 15)
        );
    }

    private NotificationOutbox matchRequestOutbox() {
        return NotificationOutbox.create(
                2001L,
                31L,
                PUSH_DEVICE_ID,
                PushProvider.FCM,
                "fcm-token",
                "새 매칭 요청",
                "지민님이 매칭을 요청했어요.",
                """
                        {
                          "notificationId": "2001",
                          "type": "SYSTEM",
                          "notificationType": "MATCH_REQUEST_RECEIVED",
                          "title": "새 매칭 요청",
                          "body": "지민님이 매칭을 요청했어요.",
                          "deeplink": "airconnect://matching/requests",
                          "sentAt": "2026-04-24T10:16:00.123456"
                        }
                        """,
                LocalDateTime.of(2026, 4, 24, 10, 16)
        );
    }
}
