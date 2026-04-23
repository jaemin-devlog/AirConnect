package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationSenderTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Test
    void send_appliesChatAndroidNotificationOptionsOnlyForChatMessageReceived() throws Exception {
        when(firebaseMessaging.send(any(Message.class))).thenReturn("provider-message-id");
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "type": "CHAT_MESSAGE",
                  "chatRoomId": "88",
                  "messageId": "5512"
                }
                """);

        sender.send(outbox);

        Object androidNotification = captureAndroidNotification();
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isEqualTo("airconnect_chat_push");
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isEqualTo("chat-88");
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isEqualTo("PRIORITY_DEFAULT");
    }

    @Test
    void send_keepsExistingAndroidNotificationOptionsForNonChatTypes() throws Exception {
        when(firebaseMessaging.send(any(Message.class))).thenReturn("provider-message-id");
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "MATCH_REQUEST_RECEIVED",
                  "type": "SYSTEM",
                  "connectionId": "312"
                }
                """);

        sender.send(outbox);

        Object androidNotification = captureAndroidNotification();
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isNull();
    }

    private Object captureAndroidNotification() throws Exception {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(messageCaptor.capture());
        Object androidConfig = ReflectionTestUtils.getField(messageCaptor.getValue(), "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("high");
        return ReflectionTestUtils.getField(androidConfig, "notification");
    }

    private NotificationOutbox outbox(String dataJson) {
        return NotificationOutbox.create(
                1041L,
                31L,
                17L,
                PushProvider.FCM,
                "fcm-token",
                "민수",
                "오늘 시간 괜찮아요?",
                dataJson,
                LocalDateTime.of(2026, 4, 23, 10, 15)
        );
    }
}
