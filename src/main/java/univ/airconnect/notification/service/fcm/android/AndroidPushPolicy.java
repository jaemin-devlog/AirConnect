package univ.airconnect.notification.service.fcm.android;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;

import java.time.Duration;

/**
 * Android FCM 발송 정책이다.
 *
 * <p>Android 클라이언트가 해석해야 하는 payload 계약과 별개로
 * FCM/Android OS에 전달할 delivery option을 서버에서 타입별로 고정한다.</p>
 */
public record AndroidPushPolicy(
        String channelId,
        boolean useNotificationPayload,
        AndroidConfig.Priority fcmPriority,
        AndroidNotification.Priority notificationPriority,
        boolean soundEnabled,
        String collapseKey,
        String notificationTag,
        Duration ttl,
        String schemaVersion
) {

    public static final String SCHEMA_VERSION = "android-fcm-v1";

    public AndroidPushPolicy {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("Android channelId is required.");
        }
        if (fcmPriority == null) {
            fcmPriority = AndroidConfig.Priority.NORMAL;
        }
        if (notificationPriority == null) {
            notificationPriority = AndroidNotification.Priority.DEFAULT;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(24);
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = SCHEMA_VERSION;
        }
    }
}
