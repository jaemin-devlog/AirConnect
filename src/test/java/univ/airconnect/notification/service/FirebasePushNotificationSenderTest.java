package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
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
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;
import univ.airconnect.notification.service.fcm.FcmDataPayloadValidator;
import univ.airconnect.notification.service.fcm.PushTargetPlatformResolver;
import univ.airconnect.notification.service.fcm.android.AndroidFcmMessageBuilder;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicyResolver;
import univ.airconnect.notification.service.fcm.ios.IosFcmMessageBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        ObjectMapper objectMapper = new ObjectMapper();
        sender = new FirebasePushNotificationSender(
                firebaseMessaging,
                new FcmDataPayloadMapper(objectMapper, new FcmDataPayloadValidator()),
                new PushTargetPlatformResolver(pushDeviceRepository),
                new AndroidFcmMessageBuilder(new AndroidPushPolicyResolver()),
                new IosFcmMessageBuilder()
        );
    }

    @Test
    void send_sendsAndroidChatAsNotificationAndData() throws Exception {
        Message message = sendAndCapture(chatOutbox(), PushPlatform.ANDROID);

        Object notification = ReflectionTestUtils.getField(message, "notification");
        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("Minsu");
        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("Hello there");
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNull();

        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        assertThat(androidConfig).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("normal");
        assertThat(ReflectionTestUtils.getField(androidConfig, "collapseKey")).isEqualTo("chat_room_88");

        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");
        assertThat(androidNotification).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isEqualTo("airconnect_chat_messages_v1");
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isEqualTo("chat_room_88");
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");

        Map<String, String> data = readData(message);
        assertThat(data)
                .containsEntry("schemaVersion", "android-fcm-v1")
                .containsEntry("notificationType", "CHAT_MESSAGE_RECEIVED")
                .containsEntry("type", "CHAT_MESSAGE")
                .containsEntry("chatRoomId", "88")
                .containsEntry("resourceType", "CHAT_ROOM")
                .containsEntry("resourceId", "88")
                .containsEntry("enqueuedAt", "2026-04-24T10:15:00.123456");
        assertThat(data)
                .doesNotContainKey("messageId")
                .doesNotContainKey("senderNickname")
                .doesNotContainKey("messagePreview")
                .doesNotContainKey("messageType")
                .doesNotContainKey("senderUserId");
    }

    @Test
    void send_keepsIosChatOnNotificationAndApnsPath() throws Exception {
        Message message = sendAndCapture(chatOutbox(), PushPlatform.IOS);

        Object notification = ReflectionTestUtils.getField(message, "notification");
        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(notification, "title")).isEqualTo("Minsu");
        assertThat(ReflectionTestUtils.getField(notification, "body")).isEqualTo("Hello there");
        assertThat(ReflectionTestUtils.getField(message, "apnsConfig")).isNotNull();
        assertThat(ReflectionTestUtils.getField(message, "androidConfig")).isNull();
    }

    @Test
    void mapFailure_doesNotTreatGenericInvalidArgumentAsInvalidToken() {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("Invalid data payload key");

        PushNotificationSender.PushSendResult result = sender.mapFailure(chatOutbox(), exception);

        assertThat(result.invalidToken()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void mapFailure_treatsRegistrationTokenInvalidArgumentAsInvalidToken() {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("The registration token is not a valid FCM registration token");

        PushNotificationSender.PushSendResult result = sender.mapFailure(chatOutbox(), exception);

        assertThat(result.invalidToken()).isTrue();
    }

    @Test
    void mapFailure_treatsUnregisteredAsInvalidToken() {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(exception.getMessage()).thenReturn("Requested entity was not found.");

        PushNotificationSender.PushSendResult result = sender.mapFailure(chatOutbox(), exception);

        assertThat(result.invalidToken()).isTrue();
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
                "Minsu",
                "Hello there",
                """
                        {
                          "notificationId": "1041",
                          "type": "CHAT_MESSAGE",
                          "notificationType": "CHAT_MESSAGE_RECEIVED",
                          "title": "Minsu",
                          "body": "Hello there",
                          "chatRoomId": "88",
                          "messageId": "5512",
                          "senderNickname": "Minsu",
                          "messagePreview": "Hello there",
                          "deeplink": "airconnect://chat/rooms/88",
                          "sentAt": "2026-04-24T10:15:00.123456",
                          "messageType": "TEXT",
                          "senderUserId": "22"
                        }
                        """,
                LocalDateTime.of(2026, 4, 24, 10, 15)
        );
    }
}
