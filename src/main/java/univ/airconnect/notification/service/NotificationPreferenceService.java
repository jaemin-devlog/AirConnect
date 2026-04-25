package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.NotificationPreference;
import univ.airconnect.notification.repository.NotificationPreferenceRepository;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Transactional
    public NotificationPreference getOrCreate(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> notificationPreferenceRepository.save(NotificationPreference.createDefault(userId)));
    }

    @Transactional
    public NotificationPreference update(Long userId, UpdateCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification preference request is required.");
        }

        validateTimezone(command.timezone());

        NotificationPreference preference = getOrCreate(userId);
        preference.update(
                command.pushEnabled(),
                command.inAppEnabled(),
                command.matchRequestEnabled(),
                command.matchResultEnabled(),
                command.groupMatchingEnabled(),
                command.chatMessageEnabled(),
                command.milestoneEnabled(),
                command.reminderEnabled(),
                command.quietHoursEnabled(),
                command.quietHoursStart(),
                command.quietHoursEnd(),
                command.timezone()
        );
        return preference;
    }

    @Transactional
    public DeliveryPolicy getDeliveryPolicy(Long userId, NotificationType type) {
        NotificationPreference preference = getOrCreate(userId);
        boolean typeEnabled = isTypeEnabled(preference, type);
        boolean inAppAllowed = Boolean.TRUE.equals(preference.getInAppEnabled()) && typeEnabled;

        if (!Boolean.TRUE.equals(preference.getPushEnabled())) {
            return DeliveryPolicy.skip(inAppAllowed, "PUSH_DISABLED");
        }
        if (!typeEnabled) {
            return DeliveryPolicy.skip(inAppAllowed, "NOTIFICATION_TYPE_DISABLED");
        }

        QuietHoursDecision quietHoursDecision = evaluateQuietHours(preference, type);
        if (quietHoursDecision.active()) {
            if (quietHoursDecision.deferable() && quietHoursDecision.nextAllowedAt() != null) {
                return DeliveryPolicy.defer(inAppAllowed, quietHoursDecision.nextAllowedAt(), "QUIET_HOURS");
            }
            return DeliveryPolicy.skip(inAppAllowed, "QUIET_HOURS");
        }
        return DeliveryPolicy.sendNow(inAppAllowed);
    }

    @Transactional
    public boolean isPushAllowed(Long userId, NotificationType type) {
        return getDeliveryPolicy(userId, type).pushAllowed();
    }

    @Transactional
    public boolean isInAppAllowed(Long userId, NotificationType type) {
        return getDeliveryPolicy(userId, type).inAppAllowed();
    }

    private boolean isTypeEnabled(NotificationPreference preference, NotificationType type) {
        return switch (type) {
            case MATCH_REQUEST_RECEIVED ->
                    Boolean.TRUE.equals(preference.getMatchRequestEnabled());
            case MATCH_REQUEST_ACCEPTED, MATCH_REQUEST_REJECTED ->
                    Boolean.TRUE.equals(preference.getMatchResultEnabled());
            case GROUP_MATCHED, TEAM_READY_REQUIRED, TEAM_ALL_READY, TEAM_ROOM_CANCELLED,
                    TEAM_MEMBER_JOINED, TEAM_MEMBER_LEFT, TEAM_MEMBER_READY_CHANGED ->
                    Boolean.TRUE.equals(preference.getGroupMatchingEnabled());
            case CHAT_MESSAGE_RECEIVED ->
                    Boolean.TRUE.equals(preference.getChatMessageEnabled());
            case MILESTONE_REWARDED ->
                    Boolean.TRUE.equals(preference.getMilestoneEnabled());
            case APPOINTMENT_REMINDER_1H, APPOINTMENT_REMINDER_10M ->
                    Boolean.TRUE.equals(preference.getReminderEnabled());
            case SYSTEM_ANNOUNCEMENT -> true;
        };
    }

    private QuietHoursDecision evaluateQuietHours(NotificationPreference preference, NotificationType type) {
        if (!Boolean.TRUE.equals(preference.getQuietHoursEnabled())) {
            return QuietHoursDecision.inactive();
        }

        LocalTime quietHoursStart = preference.getQuietHoursStart();
        LocalTime quietHoursEnd = preference.getQuietHoursEnd();
        if (quietHoursStart == null || quietHoursEnd == null) {
            return QuietHoursDecision.inactive();
        }

        if (quietHoursStart.equals(quietHoursEnd)) {
            return new QuietHoursDecision(true, false, null);
        }

        ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC())
                .withZoneSameInstant(resolveZoneId(preference.getTimezone()));
        QuietHoursWindow quietHoursWindow = resolveQuietHoursWindow(now, quietHoursStart, quietHoursEnd);
        if (!quietHoursWindow.active()) {
            return QuietHoursDecision.inactive();
        }

        LocalDateTime nextAllowedAtUtc = quietHoursWindow.endsAt()
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        return new QuietHoursDecision(true, isDelayableDuringQuietHours(type), nextAllowedAtUtc);
    }

    private QuietHoursWindow resolveQuietHoursWindow(ZonedDateTime now,
                                                     LocalTime quietHoursStart,
                                                     LocalTime quietHoursEnd) {
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            boolean active = !now.toLocalTime().isBefore(quietHoursStart)
                    && now.toLocalTime().isBefore(quietHoursEnd);
            return new QuietHoursWindow(active, now.toLocalDate().atTime(quietHoursEnd).atZone(now.getZone()));
        }

        if (!now.toLocalTime().isBefore(quietHoursStart)) {
            return new QuietHoursWindow(true, now.toLocalDate().plusDays(1).atTime(quietHoursEnd).atZone(now.getZone()));
        }
        if (now.toLocalTime().isBefore(quietHoursEnd)) {
            return new QuietHoursWindow(true, now.toLocalDate().atTime(quietHoursEnd).atZone(now.getZone()));
        }
        return new QuietHoursWindow(false, now.toLocalDate().atTime(quietHoursEnd).atZone(now.getZone()));
    }

    private boolean isDelayableDuringQuietHours(NotificationType type) {
        return switch (type) {
            case APPOINTMENT_REMINDER_1H, APPOINTMENT_REMINDER_10M -> false;
            default -> true;
        };
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE_ID;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Invalid notification preference timezone. Falling back to Asia/Seoul: {}", timezone);
            return DEFAULT_ZONE_ID;
        }
    }

    private void validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return;
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid timezone.");
        }
    }

    public record DeliveryPolicy(PushDecision pushDecision,
                                 boolean inAppAllowed,
                                 LocalDateTime nextAllowedAt,
                                 String reason) {
        public boolean pushAllowed() {
            return pushDecision == PushDecision.SEND_NOW;
        }

        public boolean pushQueueable() {
            return pushDecision == PushDecision.SEND_NOW || pushDecision == PushDecision.DEFER;
        }

        public static DeliveryPolicy sendNow(boolean inAppAllowed) {
            return new DeliveryPolicy(PushDecision.SEND_NOW, inAppAllowed, null, "SEND_NOW");
        }

        public static DeliveryPolicy defer(boolean inAppAllowed, LocalDateTime nextAllowedAt, String reason) {
            return new DeliveryPolicy(PushDecision.DEFER, inAppAllowed, nextAllowedAt, reason);
        }

        public static DeliveryPolicy skip(boolean inAppAllowed, String reason) {
            return new DeliveryPolicy(PushDecision.SKIP, inAppAllowed, null, reason);
        }
    }

    private record QuietHoursDecision(boolean active, boolean deferable, LocalDateTime nextAllowedAt) {
        private static QuietHoursDecision inactive() {
            return new QuietHoursDecision(false, false, null);
        }
    }

    private record QuietHoursWindow(boolean active, ZonedDateTime endsAt) {
    }

    public record UpdateCommand(
            Boolean pushEnabled,
            Boolean inAppEnabled,
            Boolean matchRequestEnabled,
            Boolean matchResultEnabled,
            Boolean groupMatchingEnabled,
            Boolean chatMessageEnabled,
            Boolean milestoneEnabled,
            Boolean reminderEnabled,
            Boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String timezone
    ) {
    }
}
