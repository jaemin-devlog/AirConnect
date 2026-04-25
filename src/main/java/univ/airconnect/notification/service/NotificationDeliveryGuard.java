package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicy;
import univ.airconnect.notification.service.fcm.android.AndroidPushPolicyResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryGuard {

    private final PushDeviceRepository pushDeviceRepository;
    private final NotificationPreferenceService notificationPreferenceService;
    private final AndroidPushPolicyResolver androidPushPolicyResolver;
    private final FcmDataPayloadMapper fcmDataPayloadMapper;
    private final ObjectMapper objectMapper;

    public GuardResult evaluate(NotificationOutbox outbox) {
        if (outbox == null || outbox.getId() == null) {
            return GuardResult.fail("INVALID_OUTBOX", "Outbox is missing.");
        }

        LocalDateTime now = nowUtc();
        if (outbox.getNextAttemptAt() != null && outbox.getNextAttemptAt().isAfter(now)) {
            return GuardResult.defer(outbox.getNextAttemptAt(), "SCHEDULED_IN_FUTURE", "Outbox is not due yet.");
        }

        Optional<PushDevice> pushDeviceOptional = pushDeviceRepository.findById(outbox.getPushDeviceId());
        if (pushDeviceOptional.isEmpty()) {
            return GuardResult.skip("PUSH_DEVICE_NOT_FOUND", "Push device does not exist.");
        }

        PushDevice pushDevice = pushDeviceOptional.get();
        if (!Boolean.TRUE.equals(pushDevice.getActive())) {
            return GuardResult.skip("PUSH_DEVICE_INACTIVE", "Push device is inactive.");
        }
        if (!Boolean.TRUE.equals(pushDevice.getNotificationPermissionGranted())) {
            return GuardResult.skip("NOTIFICATION_PERMISSION_DENIED", "Notification permission is not granted.");
        }
        if (pushDevice.getPushToken() == null || pushDevice.getPushToken().isBlank()) {
            return GuardResult.skip("PUSH_TOKEN_MISSING", "Push token is missing.");
        }
        if (!pushDevice.getProvider().equals(outbox.getProvider())) {
            return GuardResult.skip("PROVIDER_MISMATCH", "Outbox provider does not match current push device provider.");
        }
        if (!pushDevice.getPushToken().equals(outbox.getTargetToken())) {
            return GuardResult.skip("STALE_PUSH_TOKEN", "Outbox target token is no longer current.");
        }

        NotificationType notificationType = resolveNotificationType(outbox).orElse(null);
        if (notificationType == null) {
            return GuardResult.fail("NOTIFICATION_TYPE_MISSING", "notificationType is missing or invalid in outbox payload.");
        }

        NotificationPreferenceService.DeliveryPolicy deliveryPolicy =
                notificationPreferenceService.getDeliveryPolicy(outbox.getUserId(), notificationType);
        if (deliveryPolicy.pushDecision() == PushDecision.SKIP) {
            return GuardResult.skip(deliveryPolicy.reason(), "Push is disabled by user preference or notification type policy.");
        }

        if (pushDevice.getPlatform() == PushPlatform.ANDROID) {
            GuardResult androidExpirationDecision = evaluateAndroidExpiration(outbox, notificationType, now);
            if (androidExpirationDecision.decision() != DeliveryDecision.SEND_NOW) {
                return androidExpirationDecision;
            }
        }

        if (deliveryPolicy.pushDecision() == PushDecision.DEFER) {
            if (pushDevice.getPlatform() == PushPlatform.ANDROID) {
                LocalDateTime nextAllowedAt = deliveryPolicy.nextAllowedAt();
                if (nextAllowedAt == null) {
                    return GuardResult.skip("QUIET_HOURS", "Quiet hours are active and no next allowed time is available.");
                }
                GuardResult expirationAfterDefer = evaluateAndroidExpiration(outbox, notificationType, nextAllowedAt);
                if (expirationAfterDefer.decision() != DeliveryDecision.SEND_NOW) {
                    return GuardResult.skip("EXPIRED_BEFORE_QUIET_HOURS_END", "Outbox expires before quiet hours end.");
                }
                return GuardResult.defer(nextAllowedAt, deliveryPolicy.reason(), "Quiet hours are active.");
            }
            return GuardResult.skip(deliveryPolicy.reason(), "Quiet hours are active for non-Android push.");
        }

        return GuardResult.sendNow();
    }

    private GuardResult evaluateAndroidExpiration(NotificationOutbox outbox,
                                                  NotificationType notificationType,
                                                  LocalDateTime referenceTime) {
        Map<String, String> data = fcmDataPayloadMapper.toMap(outbox.getDataJson());
        AndroidPushPolicy policy = androidPushPolicyResolver.resolve(notificationType, data);
        LocalDateTime createdAt = outbox.getCreatedAt();
        if (createdAt == null || policy.ttl() == null) {
            return GuardResult.sendNow();
        }
        LocalDateTime expiresAt = createdAt.plus(policy.ttl());
        if (!expiresAt.isAfter(referenceTime)) {
            return GuardResult.skip("OUTBOX_EXPIRED", "Outbox TTL has expired.");
        }
        return GuardResult.sendNow();
    }

    private Optional<NotificationType> resolveNotificationType(NotificationOutbox outbox) {
        if (outbox.getDataJson() == null || outbox.getDataJson().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(outbox.getDataJson());
            if (!root.isObject()) {
                return Optional.empty();
            }
            JsonNode notificationType = root.get("notificationType");
            if (notificationType == null || notificationType.isNull() || notificationType.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(NotificationType.valueOf(notificationType.asText().trim()));
        } catch (Exception e) {
            log.warn("Failed to resolve notification type from outbox payload: outboxId={}, message={}",
                    outbox.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
