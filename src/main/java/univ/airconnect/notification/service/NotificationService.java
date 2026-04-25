package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPayloadType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final PushDeviceService pushDeviceService;
    private final AndroidChatPushCoalescingService androidChatPushCoalescingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Notification createAndEnqueue(CreateCommand command) {
        validate(command);

        NotificationPreferenceService.DeliveryPolicy deliveryPolicy =
                notificationPreferenceService.getDeliveryPolicy(command.userId(), command.type());
        CreateResult createResult = createInternal(command, deliveryPolicy);
        Notification notification = createResult.notification();

        if (!deliveryPolicy.pushQueueable()) {
            log.debug("Push skipped by preference: userId={}, type={}, reason={}",
                    command.userId(), command.type(), deliveryPolicy.reason());
            return notification;
        }

        if (!createResult.created()) {
            log.debug("Outbox skipped for deduplicated notification: notificationId={}, userId={}, type={}",
                    notification.getId(), command.userId(), command.type());
            return notification;
        }

        List<PushDevice> pushDevices = pushDeviceService.findPushableDevices(command.userId());
        if (pushDevices.isEmpty()) {
            log.debug("No pushable devices found: userId={}", command.userId());
            return notification;
        }

        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        LocalDateTime baseNextAttemptAt = deliveryPolicy.pushDecision() == PushDecision.DEFER
                ? deliveryPolicy.nextAllowedAt()
                : now;
        if (baseNextAttemptAt == null) {
            baseNextAttemptAt = now;
        }

        String outboxPayloadJson = buildOutboxPayload(notification, now);
        List<NotificationOutbox> outboxes = new ArrayList<>();
        int coalescedCount = 0;
        for (PushDevice pushDevice : pushDevices) {
            if (deliveryPolicy.pushDecision() == PushDecision.DEFER
                    && pushDevice.getPlatform() != PushPlatform.ANDROID) {
                log.debug("Deferred quiet-hours push is queued only for Android in the first patch: userId={}, deviceId={}",
                        command.userId(), pushDevice.getDeviceId());
                continue;
            }

            AndroidChatPushCoalescingService.Decision decision =
                    androidChatPushCoalescingService.decide(pushDevice, command.type(), outboxPayloadJson, now, baseNextAttemptAt);
            LocalDateTime nextAttemptAt = laterOf(decision.nextAttemptAt(), baseNextAttemptAt);

            if (decision.existingPendingOutbox().isPresent()) {
                NotificationOutbox existingOutbox = decision.existingPendingOutbox().get();
                existingOutbox.coalesceToLatest(
                        notification.getId(),
                        pushDevice.getPushToken(),
                        notification.getTitle(),
                        notification.getBody(),
                        decision.payloadJson(),
                        nextAttemptAt
                );
                coalescedCount++;
                continue;
            }

            outboxes.add(NotificationOutbox.create(
                    notification.getId(),
                    notification.getUserId(),
                    pushDevice.getId(),
                    pushDevice.getProvider(),
                    pushDevice.getPushToken(),
                    notification.getTitle(),
                    notification.getBody(),
                    decision.payloadJson(),
                    nextAttemptAt
            ));
        }
        notificationOutboxRepository.saveAll(outboxes);
        log.info("Notification outboxes enqueued: notificationId={}, count={}, coalescedCount={}",
                notification.getId(), outboxes.size(), coalescedCount);
        return notification;
    }

    @Transactional
    public Notification create(CreateCommand command) {
        validate(command);
        NotificationPreferenceService.DeliveryPolicy deliveryPolicy =
                notificationPreferenceService.getDeliveryPolicy(command.userId(), command.type());
        return createInternal(command, deliveryPolicy).notification();
    }

    private CreateResult createInternal(CreateCommand command,
                                        NotificationPreferenceService.DeliveryPolicy deliveryPolicy) {
        if (command.dedupeKey() != null && !command.dedupeKey().isBlank()) {
            Optional<Notification> existing = notificationRepository.findByUserIdAndDedupeKey(
                    command.userId(),
                    command.dedupeKey()
            );
            if (existing.isPresent()) {
                return new CreateResult(existing.get(), false);
            }
        }

        Notification notification = Notification.create(
                command.userId(),
                command.type(),
                command.title(),
                command.body(),
                command.deeplink(),
                command.actorUserId(),
                command.imageUrl(),
                command.payloadJson(),
                command.dedupeKey()
        );

        if (!deliveryPolicy.inAppAllowed()) {
            notification.softDelete();
        }

        try {
            Notification saved = notificationRepository.save(notification);
            log.info("Notification saved: id={}, userId={}, type={}",
                    saved.getId(), saved.getUserId(), saved.getType());
            return new CreateResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            if (command.dedupeKey() == null || command.dedupeKey().isBlank()) {
                throw e;
            }
            Notification existing = notificationRepository.findByUserIdAndDedupeKey(
                            command.userId(),
                            command.dedupeKey()
                    )
                    .orElseThrow(() -> e);
            return new CreateResult(existing, false);
        }
    }

    private void validate(CreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Create command is required.");
        }
        if (command.userId() == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (command.type() == null) {
            throw new IllegalArgumentException("Notification type is required.");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("Notification title is required.");
        }
        if (command.body() == null || command.body().isBlank()) {
            throw new IllegalArgumentException("Notification body is required.");
        }
        if (command.payloadJson() == null || command.payloadJson().isBlank()) {
            throw new IllegalArgumentException("Notification payloadJson is required.");
        }
    }

    private String buildOutboxPayload(Notification notification, LocalDateTime enqueuedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        String payloadJson = notification.getPayloadJson();
        if (payloadJson != null && !payloadJson.isBlank()) {
            try {
                JsonNode payloadNode = objectMapper.readTree(payloadJson);
                if (payloadNode.isObject()) {
                    root.setAll((ObjectNode) payloadNode);
                } else {
                    root.put("payload", payloadJson);
                }
            } catch (Exception e) {
                log.warn("Failed to parse notification payload JSON. Falling back to raw payload: {}", e.getMessage());
                root.put("payload", payloadJson);
            }
        }

        root.put("schemaVersion", "android-fcm-v1");
        root.put("notificationId", String.valueOf(notification.getId()));
        root.put("type", mapPushPayloadType(notification.getType()).name());
        root.put("notificationType", notification.getType().name());
        root.put("title", notification.getTitle());
        root.put("body", notification.getBody());
        root.put("deeplink", notification.getDeeplink() != null ? notification.getDeeplink() : "/notifications/" + notification.getId());
        root.put("resourceType", resolveResourceType(notification.getType(), root));
        root.put("resourceId", resolveResourceId(notification, root));
        root.put("createdAt", notification.getCreatedAt().toString());
        root.put("enqueuedAt", enqueuedAt.toString());
        root.put("sentAt", enqueuedAt.toString());
        return stringifyPayload(root, payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
    }

    private String resolveResourceType(NotificationType notificationType, ObjectNode root) {
        String existing = textOrNull(root, "resourceType");
        if (existing != null) {
            return existing;
        }
        return notificationType.resourceType();
    }

    private String resolveResourceId(Notification notification, ObjectNode root) {
        String existing = textOrNull(root, "resourceId");
        if (existing != null) {
            return existing;
        }
        return firstNonBlank(root, notification.getType().resourceIdCandidateKeys())
                .orElse(String.valueOf(notification.getId()));
    }

    private Optional<String> firstNonBlank(ObjectNode root, String... keys) {
        for (String key : keys) {
            String value = textOrNull(root, key);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private String textOrNull(ObjectNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private LocalDateTime laterOf(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private PushPayloadType mapPushPayloadType(NotificationType notificationType) {
        return switch (notificationType) {
            case CHAT_MESSAGE_RECEIVED -> PushPayloadType.CHAT_MESSAGE;
            case SYSTEM_ANNOUNCEMENT, APPOINTMENT_REMINDER_1H, APPOINTMENT_REMINDER_10M -> PushPayloadType.NOTICE;
            default -> PushPayloadType.SYSTEM;
        };
    }

    private String stringifyPayload(ObjectNode payloadNode, String fallbackPayload) {
        try {
            return objectMapper.writeValueAsString(payloadNode);
        } catch (Exception e) {
            log.warn("Failed to serialize canonical notification payload. Falling back to original payload: {}",
                    e.getMessage());
            return fallbackPayload;
        }
    }

    public record CreateCommand(
            Long userId,
            NotificationType type,
            String title,
            String body,
            String deeplink,
            Long actorUserId,
            String imageUrl,
            String payloadJson,
            String dedupeKey
    ) {
    }

    private record CreateResult(Notification notification, boolean created) {
    }
}
