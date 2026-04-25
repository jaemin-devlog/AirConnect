package univ.airconnect.notification.service.fcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicy;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmDataPayloadMapper {

    private final ObjectMapper objectMapper;
    private final FcmDataPayloadValidator fcmDataPayloadValidator;

    /**
     * Existing iOS/common mapper path. Keep semantics stable for non-Android sends.
     */
    public Map<String, String> toMap(String dataJson) {
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
     * Android versioned contract mapper.
     */
    public Map<String, String> toAndroidMap(String dataJson) {
        Map<String, String> data = toMap(dataJson);
        backfillAndroidContractForLegacyOutbox(data);
        return fcmDataPayloadValidator.validateAndroid(data);
    }

    private void backfillAndroidContractForLegacyOutbox(Map<String, String> data) {
        if (data.isEmpty()) {
            return;
        }
        data.putIfAbsent("schemaVersion", AndroidPushPolicy.SCHEMA_VERSION);
        if (!hasText(data.get("enqueuedAt")) && hasText(data.get("sentAt"))) {
            data.put("enqueuedAt", data.get("sentAt"));
        }
        if (!hasText(data.get("deeplink")) && hasText(data.get("notificationId"))) {
            data.put("deeplink", "/notifications/" + data.get("notificationId"));
        }
        if (!hasText(data.get("resourceType"))) {
            data.put("resourceType", inferResourceType(data.get("notificationType")));
        }
        if (!hasText(data.get("resourceId"))) {
            data.put("resourceId", inferResourceId(data));
        }
    }

    private String inferResourceType(String notificationType) {
        NotificationType resolvedType = resolveNotificationType(notificationType);
        if (resolvedType == null) {
            return "NOTIFICATION";
        }
        return resolvedType.resourceType();
    }

    private String inferResourceId(Map<String, String> data) {
        NotificationType resolvedType = resolveNotificationType(data.get("notificationType"));
        if (resolvedType == null) {
            return firstNonBlank(data, "notificationId");
        }
        return firstNonBlank(data, concat(resolvedType.resourceIdCandidateKeys(), "notificationId"));
    }

    private String firstNonBlank(Map<String, String> data, String... keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private NotificationType resolveNotificationType(String notificationType) {
        if (!hasText(notificationType)) {
            return null;
        }
        try {
            return NotificationType.valueOf(notificationType.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String[] concat(String[] left, String right) {
        String[] merged = new String[left.length + 1];
        System.arraycopy(left, 0, merged, 0, left.length);
        merged[left.length] = right;
        return merged;
    }

    private String stringify(JsonNode value) throws Exception {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return objectMapper.writeValueAsString(value);
    }
}
