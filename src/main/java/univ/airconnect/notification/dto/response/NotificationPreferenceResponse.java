package univ.airconnect.notification.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.notification.domain.entity.NotificationPreference;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 모바일 클라이언트용 알림 설정 응답 모델이다.
 */
@Getter
@Builder
public class NotificationPreferenceResponse {

    private Long userId;
    private Boolean pushEnabled;
    private Boolean inAppEnabled;
    private Boolean matchRequestEnabled;
    private Boolean matchResultEnabled;
    private Boolean groupMatchingEnabled;
    private Boolean chatMessageEnabled;
    private Boolean milestoneEnabled;
    private Boolean reminderEnabled;
    private Boolean quietHoursEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private String timezone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .userId(preference.getUserId())
                .pushEnabled(preference.getPushEnabled())
                .inAppEnabled(preference.getInAppEnabled())
                .matchRequestEnabled(preference.getMatchRequestEnabled())
                .matchResultEnabled(preference.getMatchResultEnabled())
                .groupMatchingEnabled(preference.getGroupMatchingEnabled())
                .chatMessageEnabled(preference.getChatMessageEnabled())
                .milestoneEnabled(preference.getMilestoneEnabled())
                .reminderEnabled(preference.getReminderEnabled())
                .quietHoursEnabled(preference.getQuietHoursEnabled())
                .quietHoursStart(preference.getQuietHoursStart())
                .quietHoursEnd(preference.getQuietHoursEnd())
                .timezone(preference.getTimezone())
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
