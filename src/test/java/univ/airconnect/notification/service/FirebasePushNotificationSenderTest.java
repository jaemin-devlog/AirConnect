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
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebasePushNotificationSenderTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Test
    void invalidArgumentPayloadFailureDoesNotInvalidateToken() {
        assertThat(FirebasePushNotificationSender.isInvalidTokenFailure("INVALID_ARGUMENT")).isFalse();
        assertThat(FirebasePushNotificationSender.isInvalidTokenFailure("UNREGISTERED")).isTrue();
        assertThat(FirebasePushNotificationSender.isInvalidTokenFailure("SENDER_ID_MISMATCH")).isTrue();
    }

    @Test
    void send_usesHighPriorityDataOnlyMessageForAndroidChat() throws Exception {
        when(firebaseMessaging.sendAsync(any(Message.class))).thenReturn(com.google.api.core.ApiFutures.immediateFuture("provider-message-id"));
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "type": "CHAT_MESSAGE",
                  "chatRoomId": "88",
                  "messageId": "5512"
                }
                """);

        sender.send(outbox, PushPlatform.ANDROID);

        Message message = captureMessage();
        assertThat(ReflectionTestUtils.getField(message, "notification")).isNull();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(message, "data");
        assertThat(data)
                .containsEntry("notificationType", "CHAT_MESSAGE_RECEIVED")
                .containsEntry("chatRoomId", "88")
                .containsEntry("messageId", "5512");
        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("high");
        assertThat(ReflectionTestUtils.getField(androidConfig, "collapseKey")).isNull();
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(androidNotification).isNull();
    }

    @Test
    void send_keepsNotificationPayloadForIosChat() throws Exception {
        when(firebaseMessaging.sendAsync(any(Message.class))).thenReturn(com.google.api.core.ApiFutures.immediateFuture("provider-message-id"));
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "type": "CHAT_MESSAGE",
                  "chatRoomId": "88",
                  "messageId": "5512"
                }
                """);

        sender.send(outbox, PushPlatform.IOS);

        Message message = captureMessage();
        Object notification = ReflectionTestUtils.getField(message, "notification");
        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("민수");
        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("오늘 시간 괜찮아요?");
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNotNull();
    }

    @Test
    void send_keepsExistingAndroidNotificationOptionsForNonChatTypes() throws Exception {
        when(firebaseMessaging.sendAsync(any(Message.class))).thenReturn(com.google.api.core.ApiFutures.immediateFuture("provider-message-id"));
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "MATCH_REQUEST_RECEIVED",
                  "type": "SYSTEM",
                  "connectionId": "312"
                }
                """);

        sender.send(outbox, PushPlatform.ANDROID);

        Message message = captureMessage();
        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("high");
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isNull();
    }

    @Test
    void send_reducesAndroidTeamActivityPriority() throws Exception {
        when(firebaseMessaging.sendAsync(any(Message.class))).thenReturn(com.google.api.core.ApiFutures.immediateFuture("provider-message-id"));
        FirebasePushNotificationSender sender = new FirebasePushNotificationSender(firebaseMessaging, new ObjectMapper());
        NotificationOutbox outbox = outbox("""
                {
                  "notificationType": "TEAM_MEMBER_JOINED",
                  "type": "SYSTEM",
                  "teamRoomId": "55"
                }
                """);

        sender.send(outbox, PushPlatform.ANDROID);

        Message message = captureMessage();
        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("normal");
        assertThat(ReflectionTestUtils.getField(androidConfig, "collapseKey")).isEqualTo("team-activity-55");
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "priority")).isEqualTo("PRIORITY_LOW");
    }

    private Message captureMessage() throws Exception {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).sendAsync(messageCaptor.capture());
        return messageCaptor.getValue();
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
