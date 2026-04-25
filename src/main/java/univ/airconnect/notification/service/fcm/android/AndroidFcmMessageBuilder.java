package univ.airconnect.notification.service.fcm.android;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AndroidFcmMessageBuilder {

    private static final String NOTIFICATION_TYPE_KEY = "notificationType";

    private final AndroidPushPolicyResolver androidPushPolicyResolver;

    public Message build(NotificationOutbox outbox, Map<String, String> data) {
        NotificationType notificationType = resolveNotificationType(data);
        AndroidPushPolicy policy = androidPushPolicyResolver.resolve(notificationType, data);

        Message.Builder builder = Message.builder()
                .setToken(outbox.getTargetToken())
                .setAndroidConfig(buildAndroidConfig(policy));

        if (policy.useNotificationPayload()) {
            builder.setNotification(com.google.firebase.messaging.Notification.builder()
                    .setTitle(outbox.getTitle())
                    .setBody(outbox.getBody())
                    .build());
        }

        if (!data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }

    private AndroidConfig buildAndroidConfig(AndroidPushPolicy policy) {
        AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
                .setPriority(policy.fcmPriority())
                .setTtl(policy.ttl().toMillis());

        if (policy.collapseKey() != null && !policy.collapseKey().isBlank()) {
            androidConfigBuilder.setCollapseKey(policy.collapseKey());
        }

        if (policy.useNotificationPayload()) {
            androidConfigBuilder.setNotification(buildAndroidNotification(policy));
        }
        return androidConfigBuilder.build();
    }

    private AndroidNotification buildAndroidNotification(AndroidPushPolicy policy) {
        AndroidNotification.Builder notificationBuilder = AndroidNotification.builder()
                .setChannelId(policy.channelId())
                .setPriority(policy.notificationPriority());

        if (policy.soundEnabled()) {
            notificationBuilder.setSound("default");
        }

        if (policy.notificationTag() != null && !policy.notificationTag().isBlank()) {
            notificationBuilder.setTag(policy.notificationTag());
        }
        return notificationBuilder.build();
    }

    private NotificationType resolveNotificationType(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String value = data.get(NOTIFICATION_TYPE_KEY);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
