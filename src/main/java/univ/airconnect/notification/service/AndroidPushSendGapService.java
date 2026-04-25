package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AndroidPushSendGapService {

    private static final String ANDROID_PUSH_GAP_KEY = "push:android:gap:";
    private static final Duration CHAT_ROOM_GAP = Duration.ofSeconds(10);
    private static final Duration TEAM_ACTIVITY_GAP = Duration.ofSeconds(20);
    private static final Duration SYSTEM_ANNOUNCEMENT_GAP = Duration.ofSeconds(3);
    private static final Duration GAP_KEY_BUFFER = Duration.ofSeconds(30);

    private final PushDeviceRepository pushDeviceRepository;
    private final FcmDataPayloadMapper fcmDataPayloadMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public Optional<LocalDateTime> nextAllowedAt(NotificationOutbox outbox,
                                                 PushDevice pushDevice,
                                                 NotificationType notificationType,
                                                 LocalDateTime now) {
        if (outbox == null || pushDevice == null || notificationType == null || now == null) {
            return Optional.empty();
        }

        Optional<GapPolicy> gapPolicy = resolveGapPolicy(outbox, pushDevice, notificationType);
        if (gapPolicy.isEmpty()) {
            return Optional.empty();
        }

        String key = gapPolicy.get().redisKey();
        try {
            Object storedValue = redisTemplate.opsForValue().get(key);
            if (storedValue == null) {
                return Optional.empty();
            }

            LocalDateTime lastSentAt = LocalDateTime.parse(String.valueOf(storedValue));
            LocalDateTime nextAllowedAt = lastSentAt.plus(gapPolicy.get().gap());
            return nextAllowedAt.isAfter(now) ? Optional.of(nextAllowedAt) : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to evaluate Android push send gap. outboxId={}, key={}, message={}",
                    outbox.getId(), key, e.getMessage());
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void recordSent(NotificationOutbox outbox, LocalDateTime sentAt) {
        if (outbox == null || outbox.getPushDeviceId() == null || sentAt == null) {
            return;
        }

        Optional<PushDevice> pushDevice = pushDeviceRepository.findById(outbox.getPushDeviceId());
        if (pushDevice.isEmpty()) {
            return;
        }

        NotificationType notificationType = resolveNotificationType(outbox).orElse(null);
        if (notificationType == null) {
            return;
        }

        Optional<GapPolicy> gapPolicy = resolveGapPolicy(outbox, pushDevice.get(), notificationType);
        if (gapPolicy.isEmpty()) {
            return;
        }

        GapPolicy policy = gapPolicy.get();
        redisTemplate.opsForValue().set(
                policy.redisKey(),
                sentAt.toString(),
                policy.gap().plus(GAP_KEY_BUFFER)
        );
    }

    private Optional<GapPolicy> resolveGapPolicy(NotificationOutbox outbox,
                                                 PushDevice pushDevice,
                                                 NotificationType notificationType) {
        if (pushDevice.getPlatform() != PushPlatform.ANDROID || pushDevice.getId() == null) {
            return Optional.empty();
        }

        Map<String, String> data = fcmDataPayloadMapper.toMap(outbox.getDataJson());
        return switch (notificationType) {
            case CHAT_MESSAGE_RECEIVED -> gapPolicy(
                    pushDevice.getId(),
                    notificationType,
                    firstNonBlank(data, "chatRoomId", "roomId", "chatId", "resourceId"),
                    CHAT_ROOM_GAP
            );
            case TEAM_MEMBER_JOINED, TEAM_MEMBER_LEFT -> gapPolicy(
                    pushDevice.getId(),
                    notificationType,
                    firstNonBlank(data, "teamRoomId", "roomId", "teamId", "resourceId"),
                    TEAM_ACTIVITY_GAP
            );
            case SYSTEM_ANNOUNCEMENT -> Optional.of(new GapPolicy(
                    redisKey(pushDevice.getId(), notificationType, "global"),
                    SYSTEM_ANNOUNCEMENT_GAP
            ));
            default -> Optional.empty();
        };
    }

    private Optional<GapPolicy> gapPolicy(Long pushDeviceId,
                                          NotificationType notificationType,
                                          String scope,
                                          Duration gap) {
        if (pushDeviceId == null || scope == null || scope.isBlank() || gap == null || gap.isNegative() || gap.isZero()) {
            return Optional.empty();
        }

        return Optional.of(new GapPolicy(redisKey(pushDeviceId, notificationType, scope.trim()), gap));
    }

    private String redisKey(Long pushDeviceId, NotificationType notificationType, String scope) {
        return ANDROID_PUSH_GAP_KEY + pushDeviceId + ":" + notificationType.name() + ":" + scope;
    }

    private String firstNonBlank(Map<String, String> data, String... keys) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Optional<NotificationType> resolveNotificationType(NotificationOutbox outbox) {
        Map<String, String> data = fcmDataPayloadMapper.toMap(outbox.getDataJson());
        String value = data.get("notificationType");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(NotificationType.valueOf(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private record GapPolicy(String redisKey, Duration gap) {
    }
}
