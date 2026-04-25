package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AndroidChatPushCoalescingService {

    static final Duration COALESCING_WINDOW = Duration.ofSeconds(3);

    private static final String CHAT_ROOM_ID_KEY = "chatRoomId";
    private static final String ROOM_ID_KEY = "roomId";
    private static final String CHAT_ID_KEY = "chatId";
    private static final String BATCHED_MESSAGE_COUNT_KEY = "batchedMessageCount";
    private static final String BATCHED_WINDOW_STARTED_AT_KEY = "batchedWindowStartedAt";

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final ObjectMapper objectMapper;

    public Decision decide(PushDevice pushDevice,
                           NotificationType notificationType,
                           String payloadJson,
                           LocalDateTime now,
                           LocalDateTime deliveryAnchorTime) {
        if (!isCandidate(pushDevice, notificationType, payloadJson)) {
            return Decision.notCandidate(now, payloadJson);
        }

        try {
            String chatRoomId = extractChatRoomId(payloadJson).orElse(null);
            if (chatRoomId == null) {
                return Decision.notCandidate(now, payloadJson);
            }

            LocalDateTime nextAttemptAt = now.plus(COALESCING_WINDOW);
            LocalDateTime lookupBaseTime = resolveLookupBaseTime(now, deliveryAnchorTime);
            Optional<NotificationOutbox> existing = findExistingPendingOutbox(pushDevice.getId(), chatRoomId, lookupBaseTime);
            if (existing.isPresent()) {
                String mergedPayload = mergePayload(existing.get().getDataJson(), payloadJson, now);
                return Decision.candidate(nextAttemptAt, mergedPayload, existing);
            }

            return Decision.candidate(nextAttemptAt, initializePayload(payloadJson, now), Optional.empty());
        } catch (RuntimeException e) {
            log.warn("Android chat push coalescing failed. Fallback to normal enqueue: pushDeviceId={}, message={}",
                    pushDevice.getId(), e.getMessage());
            return Decision.notCandidate(now, payloadJson);
        }
    }

    private boolean isCandidate(PushDevice pushDevice, NotificationType notificationType, String payloadJson) {
        return pushDevice != null
                && pushDevice.getId() != null
                && pushDevice.getPlatform() == PushPlatform.ANDROID
                && notificationType == NotificationType.CHAT_MESSAGE_RECEIVED
                && extractChatRoomId(payloadJson).isPresent();
    }

    private Optional<NotificationOutbox> findExistingPendingOutbox(Long pushDeviceId,
                                                                   String chatRoomId,
                                                                   LocalDateTime now) {
        List<NotificationOutbox> candidates = notificationOutboxRepository.findPendingCandidatesForCoalescing(
                pushDeviceId,
                NotificationDeliveryStatus.PENDING,
                0,
                now,
                now.plus(COALESCING_WINDOW)
        );
        return candidates.stream()
                .filter(candidate -> chatRoomId.equals(extractChatRoomId(candidate.getDataJson()).orElse(null)))
                .max(Comparator.comparingLong(this::safeId));
    }

    private LocalDateTime resolveLookupBaseTime(LocalDateTime now, LocalDateTime deliveryAnchorTime) {
        if (deliveryAnchorTime == null) {
            return now;
        }
        return deliveryAnchorTime.isAfter(now.plus(COALESCING_WINDOW)) ? deliveryAnchorTime : now;
    }

    private String initializePayload(String latestPayloadJson, LocalDateTime now) {
        return rewritePayload(latestPayloadJson, root -> {
            root.put(BATCHED_MESSAGE_COUNT_KEY, 1);
            root.put(BATCHED_WINDOW_STARTED_AT_KEY, now.toString());
        });
    }

    private String mergePayload(String existingPayloadJson, String latestPayloadJson, LocalDateTime now) {
        PayloadMetadata existingMetadata = extractPayloadMetadata(existingPayloadJson);
        int nextCount = existingMetadata.batchedMessageCount() + 1;
        String windowStartedAt = existingMetadata.windowStartedAt() != null
                ? existingMetadata.windowStartedAt()
                : now.toString();

        return rewritePayload(latestPayloadJson, root -> {
            root.put(BATCHED_MESSAGE_COUNT_KEY, nextCount);
            root.put(BATCHED_WINDOW_STARTED_AT_KEY, windowStartedAt);
        });
    }

    private PayloadMetadata extractPayloadMetadata(String payloadJson) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (!root.isObject()) {
                return new PayloadMetadata(1, null);
            }
            int batchCount = root.path(BATCHED_MESSAGE_COUNT_KEY).asInt(1);
            String windowStartedAt = root.path(BATCHED_WINDOW_STARTED_AT_KEY).asText(null);
            return new PayloadMetadata(Math.max(batchCount, 1), windowStartedAt);
        } catch (Exception e) {
            log.warn("Failed to parse existing Android chat payload metadata. Falling back to latest payload: {}", e.getMessage());
            return new PayloadMetadata(1, null);
        }
    }

    private String rewritePayload(String payloadJson, PayloadCustomizer customizer) {
        try {
            JsonNode parsed = objectMapper.readTree(payloadJson);
            ObjectNode root = parsed.isObject()
                    ? (ObjectNode) parsed.deepCopy()
                    : objectMapper.createObjectNode().put("payload", payloadJson);
            customizer.customize(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to rewrite Android chat payload for coalescing. Falling back to original payload: {}", e.getMessage());
            return payloadJson;
        }
    }

    private Optional<String> extractChatRoomId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            if (!root.isObject()) {
                return Optional.empty();
            }
            return firstNonBlank(root, CHAT_ROOM_ID_KEY)
                    .or(() -> firstNonBlank(root, ROOM_ID_KEY))
                    .or(() -> firstNonBlank(root, CHAT_ID_KEY));
        } catch (Exception e) {
            log.warn("Failed to parse Android chat payload JSON for coalescing. Skip coalescing: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> firstNonBlank(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text.trim());
    }

    private long safeId(NotificationOutbox outbox) {
        return outbox.getId() != null ? outbox.getId() : Long.MIN_VALUE;
    }

    @FunctionalInterface
    private interface PayloadCustomizer {
        void customize(ObjectNode root);
    }

    private record PayloadMetadata(int batchedMessageCount, String windowStartedAt) {
    }

    public record Decision(LocalDateTime nextAttemptAt,
                           String payloadJson,
                           Optional<NotificationOutbox> existingPendingOutbox,
                           boolean candidate) {
        private static Decision notCandidate(LocalDateTime now, String payloadJson) {
            return new Decision(now, payloadJson, Optional.empty(), false);
        }

        private static Decision candidate(LocalDateTime nextAttemptAt,
                                          String payloadJson,
                                          Optional<NotificationOutbox> existingPendingOutbox) {
            return new Decision(nextAttemptAt, payloadJson, existingPendingOutbox, true);
        }
    }
}
