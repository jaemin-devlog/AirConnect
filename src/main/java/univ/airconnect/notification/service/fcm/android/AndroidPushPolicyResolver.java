package univ.airconnect.notification.service.fcm.android;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.NotificationType;

import java.time.Duration;
import java.util.Map;

/**
 * NotificationType과 payload context를 Android FCM delivery policy로 변환한다.
 */
@Component
public class AndroidPushPolicyResolver {

    private static final String CHAT_CHANNEL_ID = "airconnect_chat_messages_v1";
    private static final String MATCHING_CHANNEL_ID = "airconnect_matching_v1";
    private static final String TEAM_ACTIVITY_CHANNEL_ID = "airconnect_team_activity_v1";
    private static final String TEAM_IMPORTANT_CHANNEL_ID = "airconnect_team_important_v1";
    private static final String REMINDER_CHANNEL_ID = "airconnect_reminders_v1";
    private static final String SYSTEM_CHANNEL_ID = "airconnect_system_v1";
    private static final String MILESTONE_CHANNEL_ID = "airconnect_milestone_v1";

    public AndroidPushPolicy resolve(NotificationType notificationType, Map<String, String> data) {
        if (notificationType == null) {
            return normal(SYSTEM_CHANNEL_ID, null, null, Duration.ofHours(24));
        }

        return switch (notificationType) {
            case CHAT_MESSAGE_RECEIVED -> high(
                    CHAT_CHANNEL_ID,
                    collapse("chat_room_", firstNonBlank(data, "chatRoomId", "roomId", "chatId", "resourceId")),
                    tag("chat_room_", firstNonBlank(data, "chatRoomId", "roomId", "chatId", "resourceId")),
                    Duration.ofMinutes(5)
            );
            case MATCH_REQUEST_RECEIVED -> high(
                    MATCHING_CHANNEL_ID,
                    collapse("match_request_", firstNonBlank(data, "matchRequestId", "requestId", "resourceId")),
                    tag("match_request_", firstNonBlank(data, "matchRequestId", "requestId", "resourceId")),
                    Duration.ofMinutes(30)
            );
            case MATCH_REQUEST_ACCEPTED, MATCH_REQUEST_REJECTED, GROUP_MATCHED -> high(
                    MATCHING_CHANNEL_ID,
                    collapse("match_request_", firstNonBlank(data, "matchRequestId", "requestId", "resourceId")),
                    tag("match_request_", firstNonBlank(data, "matchRequestId", "requestId", "resourceId")),
                    Duration.ofHours(2)
            );
            case TEAM_MEMBER_JOINED, TEAM_MEMBER_LEFT, TEAM_MEMBER_READY_CHANGED -> normal(
                    TEAM_ACTIVITY_CHANNEL_ID,
                    collapse("team_room_", firstNonBlank(data, "teamRoomId", "roomId", "teamId", "resourceId")),
                    tag("team_room_", firstNonBlank(data, "teamRoomId", "roomId", "teamId", "resourceId")),
                    Duration.ofMinutes(15)
            );
            case TEAM_READY_REQUIRED, TEAM_ALL_READY, TEAM_ROOM_CANCELLED -> high(
                    TEAM_IMPORTANT_CHANNEL_ID,
                    collapse("team_room_", firstNonBlank(data, "teamRoomId", "roomId", "teamId", "resourceId")),
                    tag("team_room_", firstNonBlank(data, "teamRoomId", "roomId", "teamId", "resourceId")),
                    Duration.ofHours(1)
            );
            case APPOINTMENT_REMINDER_1H -> high(
                    REMINDER_CHANNEL_ID,
                    collapse("appointment_", firstNonBlank(data, "appointmentId", "resourceId")),
                    tag("appointment_", firstNonBlank(data, "appointmentId", "resourceId")),
                    Duration.ofHours(1)
            );
            case APPOINTMENT_REMINDER_10M -> high(
                    REMINDER_CHANNEL_ID,
                    collapse("appointment_", firstNonBlank(data, "appointmentId", "resourceId")),
                    tag("appointment_", firstNonBlank(data, "appointmentId", "resourceId")),
                    Duration.ofMinutes(10)
            );
            case MILESTONE_REWARDED -> normal(
                    MILESTONE_CHANNEL_ID,
                    collapse("milestone_", firstNonBlank(data, "milestoneId", "resourceId")),
                    tag("milestone_", firstNonBlank(data, "milestoneId", "resourceId")),
                    Duration.ofHours(6)
            );
            case SYSTEM_ANNOUNCEMENT -> normal(
                    SYSTEM_CHANNEL_ID,
                    collapse("system_announcement_", firstNonBlank(data, "resourceId", "notificationId")),
                    tag("system_announcement_", firstNonBlank(data, "resourceId", "notificationId")),
                    Duration.ofHours(24)
            );
        };
    }

    private AndroidPushPolicy high(String channelId, String collapseKey, String notificationTag, Duration ttl) {
        return new AndroidPushPolicy(
                channelId,
                true,
                AndroidConfig.Priority.HIGH,
                AndroidNotification.Priority.HIGH,
                collapseKey,
                notificationTag,
                ttl,
                AndroidPushPolicy.SCHEMA_VERSION
        );
    }

    private AndroidPushPolicy normal(String channelId, String collapseKey, String notificationTag, Duration ttl) {
        return new AndroidPushPolicy(
                channelId,
                true,
                AndroidConfig.Priority.NORMAL,
                AndroidNotification.Priority.DEFAULT,
                collapseKey,
                notificationTag,
                ttl,
                AndroidPushPolicy.SCHEMA_VERSION
        );
    }

    private String collapse(String prefix, String value) {
        return value == null ? null : prefix + value;
    }

    private String tag(String prefix, String value) {
        return value == null ? null : prefix + value;
    }

    private String firstNonBlank(Map<String, String> data, String... keys) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
