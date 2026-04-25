package univ.airconnect.notification.service;

import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.service.fcm.android.AndroidFcmMessageBuilder;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicyResolver;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidFcmMessageBuilderTest {

    private final AndroidFcmMessageBuilder builder =
            new AndroidFcmMessageBuilder(new AndroidPushPolicyResolver());

    @Test
    void build_chatMessageUsesNotificationAndPolicyDrivenAndroidConfig() {
        NotificationOutbox outbox = NotificationOutbox.create(
                100L,
                20L,
                30L,
                PushProvider.FCM,
                "token-1",
                "Minsu",
                "Hello there",
                "{\"notificationType\":\"CHAT_MESSAGE_RECEIVED\",\"chatRoomId\":\"88\"}",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        );

        Message message = builder.build(outbox, Map.ofEntries(
                Map.entry("notificationType", "CHAT_MESSAGE_RECEIVED"),
                Map.entry("chatRoomId", "88"),
                Map.entry("schemaVersion", "android-fcm-v1"),
                Map.entry("notificationId", "100"),
                Map.entry("type", "CHAT_MESSAGE"),
                Map.entry("title", "Minsu"),
                Map.entry("body", "Hello there"),
                Map.entry("deeplink", "/chat/88"),
                Map.entry("resourceType", "CHAT_ROOM"),
                Map.entry("resourceId", "88"),
                Map.entry("enqueuedAt", "2026-04-25T10:00:00")
        ));

        Object notification = ReflectionTestUtils.getField(message, "notification");
        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");

        assertThat(notification).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("normal");
        assertThat(ReflectionTestUtils.getField(androidConfig, "collapseKey")).isEqualTo("chat_room_88");
        assertThat(androidNotification).isNotNull();
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isEqualTo("airconnect_chat_messages_v1");
        assertThat(ReflectionTestUtils.getField(androidNotification, "tag")).isEqualTo("chat_room_88");
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isEqualTo("default");
    }

    @Test
    void build_teamActivityUsesQuietAndroidNotification() {
        NotificationOutbox outbox = NotificationOutbox.create(
                101L,
                21L,
                31L,
                PushProvider.FCM,
                "token-2",
                "Team activity",
                "Someone joined",
                "{\"notificationType\":\"TEAM_MEMBER_JOINED\",\"teamRoomId\":\"77\"}",
                LocalDateTime.of(2026, 4, 25, 10, 0)
        );

        Message message = builder.build(outbox, Map.ofEntries(
                Map.entry("notificationType", "TEAM_MEMBER_JOINED"),
                Map.entry("teamRoomId", "77"),
                Map.entry("schemaVersion", "android-fcm-v1"),
                Map.entry("notificationId", "101"),
                Map.entry("type", "SYSTEM"),
                Map.entry("title", "Team activity"),
                Map.entry("body", "Someone joined"),
                Map.entry("deeplink", "/matching/team-rooms/77"),
                Map.entry("resourceType", "TEAM_ROOM"),
                Map.entry("resourceId", "77"),
                Map.entry("enqueuedAt", "2026-04-25T10:00:00")
        ));

        Object androidConfig = ReflectionTestUtils.getField(message, "androidConfig");
        Object androidNotification = ReflectionTestUtils.getField(androidConfig, "notification");

        assertThat(ReflectionTestUtils.getField(androidConfig, "priority")).isEqualTo("normal");
        assertThat(ReflectionTestUtils.getField(androidNotification, "channelId")).isEqualTo("airconnect_team_activity_v1");
        assertThat(ReflectionTestUtils.getField(androidNotification, "sound")).isNull();
    }
}
