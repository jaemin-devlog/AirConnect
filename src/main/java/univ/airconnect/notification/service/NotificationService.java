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
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 알림 원본을 저장하고 필요할 때 푸시 outbox 행을 적재한다.
 */
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

    /**
     * 알림을 저장하고 푸시가 허용된 경우 즉시 outbox를 적재한다.
     */
    @Transactional
    public Notification createAndEnqueue(CreateCommand command) {
        validate(command);

        NotificationPreferenceService.DeliveryPolicy deliveryPolicy =
                notificationPreferenceService.getDeliveryPolicy(command.userId(), command.type());
        CreateResult createResult = createInternal(command, deliveryPolicy);
        Notification notification = createResult.notification();

        if (!deliveryPolicy.pushAllowed()) {
            log.debug("Push skipped by preference or quiet hours: userId={}, type={}",
                    command.userId(), command.type());
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

        LocalDateTime now = LocalDateTime.now();
        String outboxPayloadJson = buildOutboxPayload(notification, now);
        List<NotificationOutbox> outboxes = new ArrayList<>();
        int coalescedCount = 0;
        for (PushDevice pushDevice : pushDevices) {
            AndroidChatPushCoalescingService.Decision decision =
                    androidChatPushCoalescingService.decide(pushDevice, command.type(), outboxPayloadJson, now);

            if (decision.existingPendingOutbox().isPresent()) {
                NotificationOutbox existingOutbox = decision.existingPendingOutbox().get();
                existingOutbox.coalesceToLatest(
                        notification.getId(),
                        pushDevice.getPushToken(),
                        notification.getTitle(),
                        notification.getBody(),
                        decision.payloadJson(),
                        decision.nextAttemptAt()
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
                    decision.nextAttemptAt()
            ));
        }
        notificationOutboxRepository.saveAll(outboxes);
        log.info("Notification outboxes enqueued: notificationId={}, count={}, coalescedCount={}",
                notification.getId(), outboxes.size(), coalescedCount);
        return notification;
    }

    /**
     * 알림 원본만 저장한다.
     */
    @Transactional
    public Notification create(CreateCommand command) {
        validate(command);
        NotificationPreferenceService.DeliveryPolicy deliveryPolicy =
                notificationPreferenceService.getDeliveryPolicy(command.userId(), command.type());
        return createInternal(command, deliveryPolicy).notification();
    }

    /**
     * 알림을 한 번만 저장하고 새 행이 생성됐는지 함께 반환한다.
     */
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

        // outbox 원본 행은 유지하되, 인앱 채널이 꺼져 있으면 알림함에서는 숨긴다.
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

    /**
     * 알림 생성에 필요한 최소 필드를 검증한다.
     */
    private void validate(CreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("요청 명령은 필수입니다.");
        }
        if (command.userId() == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (command.type() == null) {
            throw new IllegalArgumentException("알림 유형은 필수입니다.");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("알림 제목은 필수입니다.");
        }
        if (command.body() == null || command.body().isBlank()) {
            throw new IllegalArgumentException("알림 본문은 필수입니다.");
        }
        if (command.payloadJson() == null || command.payloadJson().isBlank()) {
            throw new IllegalArgumentException("알림 payloadJson은 필수입니다.");
        }
    }

    /**
     * 모든 푸시 발송에 공통으로 사용하는 정규화된 FCM data payload를 만든다.
     */
    private String buildOutboxPayload(Notification notification, LocalDateTime sentAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("notificationId", String.valueOf(notification.getId()));
        root.put("type", mapPushPayloadType(notification.getType()).name());
        root.put("notificationType", notification.getType().name());
        root.put("title", notification.getTitle());
        root.put("body", notification.getBody());
        root.put("deeplink", notification.getDeeplink() != null ? notification.getDeeplink() : "");
        root.put("sentAt", sentAt.toString());

        String payloadJson = notification.getPayloadJson();
        if (payloadJson == null || payloadJson.isBlank()) {
            return stringifyPayload(root, "{}");
        }

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

        // 도메인 payload에 같은 이름의 키가 있어도 앱 공통 키는 항상 이 값으로 고정한다.
        root.put("notificationId", String.valueOf(notification.getId()));
        root.put("type", mapPushPayloadType(notification.getType()).name());
        root.put("notificationType", notification.getType().name());
        root.put("title", notification.getTitle());
        root.put("body", notification.getBody());
        root.put("deeplink", notification.getDeeplink() != null ? notification.getDeeplink() : "");
        root.put("sentAt", sentAt.toString());
        return stringifyPayload(root, payloadJson);
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

    /**
     * 알림 생성 입력 모델이다.
     */
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

    /**
     * dedupe 결과로 기존 행을 재사용했는지 알려주는 내부 결과 모델이다.
     */
    private record CreateResult(Notification notification, boolean created) {
    }
}
