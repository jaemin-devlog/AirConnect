package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Firebase Admin SDK를 사용해 실제 푸시를 발송하는 구현체이다.
 *
 * <p>Android 기기는 FCM으로 직접 전달받고,
 * iOS 기기는 FCM 토큰으로 등록한 뒤 Firebase가 APNs로 중계한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.push.fcm", name = "enabled", havingValue = "true")
public class FirebasePushNotificationSender implements PushNotificationSender {

    private static final String NOTIFICATION_TYPE_KEY = "notificationType";
    private static final String CHAT_MESSAGE_RECEIVED_TYPE = "CHAT_MESSAGE_RECEIVED";
    private static final String TEAM_MEMBER_JOINED_TYPE = "TEAM_MEMBER_JOINED";
    private static final String TEAM_MEMBER_LEFT_TYPE = "TEAM_MEMBER_LEFT";
    private static final String TEAM_MEMBER_READY_CHANGED_TYPE = "TEAM_MEMBER_READY_CHANGED";
    private static final String TEAM_ROOM_ID_KEY = "teamRoomId";
    private static final String CHAT_ROOM_ID_KEY = "chatRoomId";
    private static final String CHAT_PUSH_CHANNEL_ID = "airconnect_chat_push";
    private static final String CHAT_PUSH_TAG_PREFIX = "chat-";
    private static final String CHAT_COLLAPSE_KEY_PREFIX = "chat-room-";
    private static final String TEAM_ACTIVITY_COLLAPSE_KEY_PREFIX = "team-activity-";

    private static final Set<String> INVALID_TOKEN_ERROR_CODES = Set.of(
            "UNREGISTERED",
            "SENDER_ID_MISMATCH",
            "INVALID_ARGUMENT"
    );

    private static final Set<String> RETRYABLE_PLATFORM_ERROR_CODES = Set.of(
            "INTERNAL",
            "UNAVAILABLE",
            "DEADLINE_EXCEEDED",
            "RESOURCE_EXHAUSTED"
    );

    private static final Set<String> RETRYABLE_MESSAGING_ERROR_CODES = Set.of(
            "INTERNAL",
            "UNAVAILABLE",
            "QUOTA_EXCEEDED",
            "THIRD_PARTY_AUTH_ERROR"
    );

    private final FirebaseMessaging firebaseMessaging;
    private final ObjectMapper objectMapper;

    /**
     * outbox 한 건을 Firebase Cloud Messaging으로 발송한다.
     */
    @Override
    public PushSendResult send(NotificationOutbox outbox) {
        if (outbox.getProvider() != PushProvider.FCM) {
            return PushSendResult.failed(
                    "UNSUPPORTED_PROVIDER",
                    "현재는 FCM 전송만 구현되어 있습니다. iOS 기기도 FCM 토큰으로 등록해 주세요."
            );
        }

        try {
            String providerMessageId = firebaseMessaging.send(buildMessage(outbox));
            log.info("Push dispatched via FCM: outboxId={}, messageId={}", outbox.getId(), providerMessageId);
            return PushSendResult.success(providerMessageId);
        } catch (FirebaseMessagingException e) {
            return mapFailure(outbox, e);
        }
    }

    /**
     * 공통 알림 본문, Android 설정, APNs 설정을 포함한 FCM 메시지를 구성한다.
     */
    private Message buildMessage(NotificationOutbox outbox) {
        Map<String, String> data = buildDataMap(outbox.getDataJson());

        Message.Builder builder = Message.builder()
                .setToken(outbox.getTargetToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(outbox.getTitle())
                        .setBody(outbox.getBody())
                        .build())
                .setAndroidConfig(buildAndroidConfig(data))
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-priority", "10")
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build());

        if (!data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }

    private AndroidConfig buildAndroidConfig(Map<String, String> data) {
        String notificationType = data.get(NOTIFICATION_TYPE_KEY);
        AndroidNotification.Builder notificationBuilder = AndroidNotification.builder();
        AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder();

        if (isChatMessageReceived(data)) {
            notificationBuilder
                    .setChannelId(CHAT_PUSH_CHANNEL_ID)
                    .setPriority(AndroidNotification.Priority.DEFAULT);

            String chatRoomId = data.get(CHAT_ROOM_ID_KEY);
            if (chatRoomId != null && !chatRoomId.isBlank()) {
                notificationBuilder.setTag(CHAT_PUSH_TAG_PREFIX + chatRoomId.trim());
                androidConfigBuilder.setCollapseKey(CHAT_COLLAPSE_KEY_PREFIX + chatRoomId.trim());
            }

            notificationBuilder.setSound("default");
            androidConfigBuilder.setPriority(AndroidConfig.Priority.NORMAL);
        } else if (isLowValueTeamActivity(notificationType)) {
            notificationBuilder.setPriority(AndroidNotification.Priority.LOW);

            String teamRoomId = data.get(TEAM_ROOM_ID_KEY);
            if (teamRoomId != null && !teamRoomId.isBlank()) {
                androidConfigBuilder.setCollapseKey(TEAM_ACTIVITY_COLLAPSE_KEY_PREFIX + teamRoomId.trim());
            }

            androidConfigBuilder.setPriority(AndroidConfig.Priority.NORMAL);
        } else {
            notificationBuilder.setSound("default");
            androidConfigBuilder.setPriority(AndroidConfig.Priority.HIGH);
        }

        return androidConfigBuilder
                .setNotification(notificationBuilder.build())
                .build();
    }

    private boolean isChatMessageReceived(Map<String, String> data) {
        return CHAT_MESSAGE_RECEIVED_TYPE.equals(data.get(NOTIFICATION_TYPE_KEY));
    }

    private boolean isLowValueTeamActivity(String notificationType) {
        return TEAM_MEMBER_JOINED_TYPE.equals(notificationType)
                || TEAM_MEMBER_LEFT_TYPE.equals(notificationType)
                || TEAM_MEMBER_READY_CHANGED_TYPE.equals(notificationType);
    }

    /**
     * 저장된 JSON payload를 FCM data 메시지 형식의 평탄한 문자열 맵으로 변환한다.
     */
    private Map<String, String> buildDataMap(String dataJson) {
        Map<String, String> data = new LinkedHashMap<>();
        if (dataJson == null || dataJson.isBlank()) {
            return data;
        }

        try {
            JsonNode root = objectMapper.readTree(dataJson);
            if (!root.isObject()) {
                data.put("payload", dataJson);
                return data;
            }

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String value = stringify(field.getValue());
                if (value != null) {
                    data.put(field.getKey(), value);
                }
            }
            return data;
        } catch (Exception e) {
            log.warn("Failed to parse notification payload JSON. Falling back to raw payload: {}", e.getMessage());
            data.put("payload", dataJson);
            return data;
        }
    }

    /**
     * JSON 노드 하나를 FCM data payload에서 허용하는 문자열 형태로 변환한다.
     */
    private String stringify(JsonNode value) throws Exception {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return objectMapper.writeValueAsString(value);
    }

    /**
     * Firebase 예외를 워커가 처리할 재시도/무효 토큰/최종 실패 결과로 변환한다.
     */
    private PushSendResult mapFailure(NotificationOutbox outbox, FirebaseMessagingException e) {
        String platformErrorCode = enumName(e);
        String messagingErrorCode = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : null;
        String errorCode = messagingErrorCode != null ? messagingErrorCode : platformErrorCode;
        String errorMessage = e.getMessage() != null ? e.getMessage() : "Firebase messaging request failed";

        log.warn("FCM push failed: outboxId={}, errorCode={}, messagingErrorCode={}, message={}",
                outbox.getId(), platformErrorCode, messagingErrorCode, errorMessage);

        if (isInvalidTokenFailure(messagingErrorCode, errorMessage)) {
            return PushSendResult.invalidToken(errorCode, errorMessage);
        }
        if (isRetryableFailure(platformErrorCode, messagingErrorCode)) {
            return PushSendResult.retryableFailure(errorCode, errorMessage);
        }
        return PushSendResult.failed(errorCode, errorMessage);
    }

    private boolean isInvalidTokenFailure(String messagingErrorCode, String errorMessage) {
        if (messagingErrorCode != null && INVALID_TOKEN_ERROR_CODES.contains(messagingErrorCode)) {
            return true;
        }
        return "INVALID_ARGUMENT".equals(messagingErrorCode)
                || (errorMessage != null
                && errorMessage.toLowerCase().contains("registration token"));
    }

    private boolean isRetryableFailure(String platformErrorCode, String messagingErrorCode) {
        return RETRYABLE_PLATFORM_ERROR_CODES.contains(platformErrorCode)
                || (messagingErrorCode != null && RETRYABLE_MESSAGING_ERROR_CODES.contains(messagingErrorCode));
    }

    private String enumName(FirebaseException exception) {
        return exception.getErrorCode() != null ? exception.getErrorCode().name() : "FCM_ERROR";
    }
}
