package univ.airconnect.notification.service.fcm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.NotificationType;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class FcmDataPayloadValidator {

    private static final int MAX_DATA_PAYLOAD_BYTES = 4096;

    private static final Set<String> ALLOWED_ANDROID_KEYS = Set.of(
            "schemaVersion",
            "notificationId",
            "notificationType",
            "type",
            "title",
            "body",
            "deeplink",
            "resourceType",
            "resourceId",
            "createdAt",
            "enqueuedAt",
            "sentAt",
            "actorUserId",
            "imageUrl",
            "chatRoomId",
            "roomId",
            "chatId",
            "matchRequestId",
            "requestId",
            "teamRoomId",
            "teamId",
            "appointmentId",
            "milestoneId",
            "batchedMessageCount",
            "batchedWindowStartedAt"
    );

    private static final Set<String> REQUIRED_ANDROID_KEYS = Set.of(
            "schemaVersion",
            "notificationId",
            "notificationType",
            "title",
            "body",
            "deeplink",
            "resourceType",
            "resourceId",
            "enqueuedAt"
    );

    private static final Set<String> RESERVED_KEYS = Set.of(
            "from",
            "gcm",
            "google",
            "google.c.a.e",
            "message_type",
            "collapse_key"
    );

    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password",
            "passwd",
            "token",
            "secret",
            "authorization",
            "credential",
            "phone",
            "email"
    );

    public Map<String, String> validateAndroid(Map<String, String> rawData) {
        Map<String, String> normalized = new LinkedHashMap<>();
        List<String> droppedKeys = new ArrayList<>();
        if (rawData == null || rawData.isEmpty()) {
            throw new FcmDataPayloadValidationException("Android FCM data payload is empty.");
        }

        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (key == null) {
                continue;
            }
            if (isReservedKey(key)) {
                throw new FcmDataPayloadValidationException("Reserved FCM data key is not allowed: " + key);
            }
            if (!ALLOWED_ANDROID_KEYS.contains(key)) {
                droppedKeys.add(key);
                continue;
            }
            if (looksSensitive(key)) {
                throw new FcmDataPayloadValidationException("Sensitive key is not allowed in Android FCM data: " + key);
            }
            String value = normalizeValue(entry.getValue());
            if (value != null) {
                normalized.put(key, value);
            }
        }

        validateRequiredKeys(normalized);
        validateResourceContract(rawData, normalized);
        validateDeeplink(normalized.get("deeplink"));
        validateSize(normalized);
        if (!droppedKeys.isEmpty()) {
            log.warn("Dropped unsupported Android FCM data keys: {}", droppedKeys);
        }
        return normalized;
    }

    private void validateRequiredKeys(Map<String, String> data) {
        for (String key : REQUIRED_ANDROID_KEYS) {
            String value = data.get(key);
            if (value == null || value.isBlank()) {
                throw new FcmDataPayloadValidationException("Required Android FCM data key is missing: " + key);
            }
        }
    }

    private void validateResourceContract(Map<String, String> rawData, Map<String, String> normalizedData) {
        String notificationTypeValue = normalizedData.get("notificationType");
        NotificationType notificationType = resolveNotificationType(notificationTypeValue);
        String expectedResourceType = notificationType.resourceType();
        String actualResourceType = normalizedData.get("resourceType");
        if (!expectedResourceType.equals(actualResourceType)) {
            throw new FcmDataPayloadValidationException(
                    "resourceType does not match notificationType. expected="
                            + expectedResourceType + ", actual=" + actualResourceType
            );
        }

        String resourceId = normalizedData.get("resourceId");
        if (resourceId == null || resourceId.isBlank()) {
            throw new FcmDataPayloadValidationException("resourceId is required for Android routing.");
        }

        String canonicalResourceId = firstNonBlank(rawData, notificationType.resourceIdCandidateKeys());
        if (canonicalResourceId != null && !canonicalResourceId.equals(resourceId.trim())) {
            throw new FcmDataPayloadValidationException(
                    "resourceId does not match notificationType contract. expected="
                            + canonicalResourceId + ", actual=" + resourceId
            );
        }
    }

    private void validateDeeplink(String deeplink) {
        if (deeplink == null || deeplink.isBlank()) {
            return;
        }
        if (deeplink.startsWith("/")) {
            return;
        }
        try {
            URI uri = new URI(deeplink);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                throw new FcmDataPayloadValidationException("Deeplink must be absolute URI or app path.");
            }
        } catch (URISyntaxException e) {
            throw new FcmDataPayloadValidationException("Invalid deeplink format: " + deeplink);
        }
    }

    private void validateSize(Map<String, String> data) {
        int bytes = 0;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            bytes += entry.getKey().getBytes(StandardCharsets.UTF_8).length;
            bytes += entry.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        if (bytes > MAX_DATA_PAYLOAD_BYTES) {
            throw new FcmDataPayloadValidationException(
                    "Android FCM data payload exceeds " + MAX_DATA_PAYLOAD_BYTES + " bytes: " + bytes
            );
        }
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim();
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    private boolean isReservedKey(String key) {
        String lowerKey = key.toLowerCase();
        return RESERVED_KEYS.contains(lowerKey)
                || lowerKey.startsWith("google.")
                || lowerKey.startsWith("gcm.");
    }

    private boolean looksSensitive(String key) {
        String lowerKey = key.toLowerCase();
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(lowerKey::contains);
    }

    private NotificationType resolveNotificationType(String rawNotificationType) {
        if (rawNotificationType == null || rawNotificationType.isBlank()) {
            throw new FcmDataPayloadValidationException("notificationType is required.");
        }
        try {
            return NotificationType.valueOf(rawNotificationType.trim());
        } catch (IllegalArgumentException e) {
            throw new FcmDataPayloadValidationException("Unsupported notificationType: " + rawNotificationType);
        }
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
