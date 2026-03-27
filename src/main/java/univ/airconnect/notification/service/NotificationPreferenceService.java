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

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 사용자별 알림 설정을 해석하고 필요 시 기본 설정 행을 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    /**
     * 사용자의 설정 행을 조회하고, 없으면 기본 설정 행을 생성한다.
     */
    @Transactional
    public NotificationPreference getOrCreate(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> notificationPreferenceRepository.save(NotificationPreference.createDefault(userId)));
    }

    /**
     * 알림 설정 부분 수정 요청을 적용한다.
     */
    @Transactional
    public NotificationPreference update(Long userId, UpdateCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알림 설정 요청 본문이 필요합니다.");
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

    /**
     * 알림 타입별로 앱 내 노출과 푸시 발송 허용 여부를 함께 계산한다.
     */
    @Transactional
    public DeliveryPolicy getDeliveryPolicy(Long userId, NotificationType type) {
        NotificationPreference preference = getOrCreate(userId);
        boolean typeEnabled = isTypeEnabled(preference, type);
        boolean inAppAllowed = Boolean.TRUE.equals(preference.getInAppEnabled()) && typeEnabled;
        boolean pushAllowed = Boolean.TRUE.equals(preference.getPushEnabled())
                && typeEnabled
                && !isQuietHoursActive(preference);
        return new DeliveryPolicy(pushAllowed, inAppAllowed);
    }

    /**
     * 주어진 사용자와 알림 타입에 대해 푸시 발송이 허용되는지 반환한다.
     */
    @Transactional
    public boolean isPushAllowed(Long userId, NotificationType type) {
        return getDeliveryPolicy(userId, type).pushAllowed();
    }

    /**
     * 알림이 앱 내 알림함에 계속 노출되어야 하는지 반환한다.
     */
    @Transactional
    public boolean isInAppAllowed(Long userId, NotificationType type) {
        return getDeliveryPolicy(userId, type).inAppAllowed();
    }

    /**
     * 알림 타입을 해당 설정 플래그와 매핑한다.
     */
    private boolean isTypeEnabled(NotificationPreference preference, NotificationType type) {
        return switch (type) {
            case MATCH_REQUEST_RECEIVED ->
                    Boolean.TRUE.equals(preference.getMatchRequestEnabled());
            case MATCH_REQUEST_ACCEPTED, MATCH_REQUEST_REJECTED ->
                    Boolean.TRUE.equals(preference.getMatchResultEnabled());
            case GROUP_MATCHED, TEAM_READY_REQUIRED, TEAM_ALL_READY, TEAM_ROOM_CANCELLED,
                    TEAM_MEMBER_JOINED, TEAM_MEMBER_LEFT ->
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

    /**
     * 사용자의 방해금지 시간대를 푸시 발송 여부에만 적용한다.
     */
    private boolean isQuietHoursActive(NotificationPreference preference) {
        if (!Boolean.TRUE.equals(preference.getQuietHoursEnabled())) {
            return false;
        }

        LocalTime quietHoursStart = preference.getQuietHoursStart();
        LocalTime quietHoursEnd = preference.getQuietHoursEnd();
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        LocalTime currentTime = ZonedDateTime.now(resolveZoneId(preference.getTimezone())).toLocalTime();
        if (quietHoursStart.equals(quietHoursEnd)) {
            return true;
        }
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !currentTime.isBefore(quietHoursStart) && currentTime.isBefore(quietHoursEnd);
        }
        return !currentTime.isBefore(quietHoursStart) || currentTime.isBefore(quietHoursEnd);
    }

    /**
     * 잘못된 시간대 문자열이 저장돼 있으면 Asia/Seoul로 대체한다.
     */
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효하지 않은 timezone 입니다.");
        }
    }

    /**
     * 사용자 한 명과 알림 타입 하나에 대한 최종 전달 정책이다.
     */
    public record DeliveryPolicy(boolean pushAllowed, boolean inAppAllowed) {
    }

    /**
     * 알림 설정 부분 수정 요청 모델이다.
     */
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
