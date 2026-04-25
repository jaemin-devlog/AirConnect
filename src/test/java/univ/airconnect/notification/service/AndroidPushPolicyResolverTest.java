package univ.airconnect.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import org.junit.jupiter.api.Test;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicy;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicyResolver;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidPushPolicyResolverTest {

    private final AndroidPushPolicyResolver resolver = new AndroidPushPolicyResolver();

    @Test
    void resolve_chatMessageUsesChatChannelAndNormalPriority() {
        AndroidPushPolicy policy = resolver.resolve(
                NotificationType.CHAT_MESSAGE_RECEIVED,
                Map.of("chatRoomId", "123")
        );

        assertThat(policy.channelId()).isEqualTo("airconnect_chat_messages_v1");
        assertThat(policy.useNotificationPayload()).isTrue();
        assertThat(policy.fcmPriority()).isEqualTo(AndroidConfig.Priority.NORMAL);
        assertThat(policy.notificationPriority()).isEqualTo(AndroidNotification.Priority.DEFAULT);
        assertThat(policy.soundEnabled()).isTrue();
        assertThat(policy.collapseKey()).isEqualTo("chat_room_123");
        assertThat(policy.notificationTag()).isEqualTo("chat_room_123");
        assertThat(policy.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.schemaVersion()).isEqualTo("android-fcm-v1");
    }

    @Test
    void resolve_teamActivityUsesNormalPriority() {
        AndroidPushPolicy policy = resolver.resolve(
                NotificationType.TEAM_MEMBER_JOINED,
                Map.of("teamRoomId", "77")
        );

        assertThat(policy.channelId()).isEqualTo("airconnect_team_activity_v1");
        assertThat(policy.fcmPriority()).isEqualTo(AndroidConfig.Priority.NORMAL);
        assertThat(policy.notificationPriority()).isEqualTo(AndroidNotification.Priority.DEFAULT);
        assertThat(policy.soundEnabled()).isFalse();
        assertThat(policy.collapseKey()).isEqualTo("team_room_77");
    }
}
