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
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;

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
    private static final String CHAT_ROOM_ID_KEY = "chatRoomId";
    private static final String ROOM_ID_KEY = "roomId";
    private static final String CHAT_ID_KEY = "chatId";
    private static final String CHAT_COLLAPSE_KEY_PREFIX = "chat_room_";
    private static final String CHAT_PUSH_CHANNEL_ID = "airconnect_chat_push_v2";
    private static final String CHAT_PUSH_TAG_PREFIX = "chat-";

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
    private final PushDeviceRepository pushDeviceRepository;

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
     * FCM 메시지를 구성한다.
     *
     * <p>Android 채팅 푸시는 앱의 onMessageReceived() 경로를 보장하기 위해 data-only로 보낸다.
     * iOS 채팅과 그 외 푸시는 기존 notification/APNs 경로를 유지한다.</p>
     */
    private Message buildMessage(NotificationOutbox outbox) {
        Map<String, String> data = buildDataMap(outbox.getDataJson());
        PushPlatform platform = resolveTargetPlatform(outbox);
        boolean androidChatMessage = isAndroidChatMessageReceived(platform, data);

        Message.Builder builder = Message.builder()
                .setToken(outbox.getTargetToken())
                .setAndroidConfig(androidChatMessage
                        ? buildAndroidDataOnlyConfig(data)
                        : buildAndroidNotificationConfig(data));

        if (!androidChatMessage) {
            builder.setNotification(com.google.firebase.messaging.Notification.builder()
                    .setTitle(outbox.getTitle())
                    .setBody(outbox.getBody())
                    .build());
            builder.setApnsConfig(buildApnsConfig());
        }

        if (!data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }

    private PushPlatform resolveTargetPlatform(NotificationOutbox outbox) {
        return pushDeviceRepository.findById(outbox.getPushDeviceId())
                .map(PushDevice::getPlatform)
                .orElseGet(() -> {
                    log.warn("Push device not found while building FCM message. Falling back to generic payload: outboxId={}, pushDeviceId={}",
                            outbox.getId(), outbox.getPushDeviceId());
                    return null;
                });
    }

    private ApnsConfig buildApnsConfig() {
        return ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder()
                        .setSound("default")
                        .build())
                .build();
    }

    private AndroidConfig buildAndroidDataOnlyConfig(Map<String, String> data) {
        AndroidConfig.Builder builder = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH);
        String collapseKey = buildChatRoomCollapseKey(data);
        if (collapseKey != null) {
            builder.setCollapseKey(collapseKey);
        }
        return builder.build();
    }

    private String buildChatRoomCollapseKey(Map<String, String> data) {
        String chatRoomId = firstNonBlank(data, CHAT_ROOM_ID_KEY, ROOM_ID_KEY, CHAT_ID_KEY);
        if (chatRoomId == null) {
            return null;
        }
        return CHAT_COLLAPSE_KEY_PREFIX + chatRoomId;
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

    private AndroidConfig buildAndroidNotificationConfig(Map<String, String> data) {
        AndroidNotification.Builder notificationBuilder = AndroidNotification.builder()
                .setSound("default");

        if (isChatMessageReceived(data)) {
            notificationBuilder
                    .setChannelId(CHAT_PUSH_CHANNEL_ID)
                    .setPriority(AndroidNotification.Priority.DEFAULT);

            String chatRoomId = firstNonBlank(data, CHAT_ROOM_ID_KEY, ROOM_ID_KEY, CHAT_ID_KEY);
            if (chatRoomId != null) {
                notificationBuilder.setTag(CHAT_PUSH_TAG_PREFIX + chatRoomId);
            }
        }

        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(notificationBuilder.build())
                .build();
    }

    private boolean isAndroidChatMessageReceived(PushPlatform platform, Map<String, String> data) {
        return platform == PushPlatform.ANDROID && isChatMessageReceived(data);
    }

    private boolean isChatMessageReceived(Map<String, String> data) {
        return CHAT_MESSAGE_RECEIVED_TYPE.equals(data.get(NOTIFICATION_TYPE_KEY));
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
