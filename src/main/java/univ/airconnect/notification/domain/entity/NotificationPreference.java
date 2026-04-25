package univ.airconnect.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled;

    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled;

    @Column(name = "match_request_enabled", nullable = false)
    private Boolean matchRequestEnabled;

    @Column(name = "match_result_enabled", nullable = false)
    private Boolean matchResultEnabled;

    @Column(name = "group_matching_enabled", nullable = false)
    private Boolean groupMatchingEnabled;

    @Column(name = "chat_message_enabled", nullable = false)
    private Boolean chatMessageEnabled;

    @Column(name = "milestone_enabled", nullable = false)
    private Boolean milestoneEnabled;

    @Column(name = "reminder_enabled", nullable = false)
    private Boolean reminderEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private Boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private NotificationPreference(Long userId,
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
                                   String timezone) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        this.userId = userId;
        this.pushEnabled = valueOrDefault(pushEnabled, true);
        this.inAppEnabled = valueOrDefault(inAppEnabled, true);
        this.matchRequestEnabled = valueOrDefault(matchRequestEnabled, true);
        this.matchResultEnabled = valueOrDefault(matchResultEnabled, true);
        this.groupMatchingEnabled = valueOrDefault(groupMatchingEnabled, true);
        this.chatMessageEnabled = valueOrDefault(chatMessageEnabled, true);
        this.milestoneEnabled = valueOrDefault(milestoneEnabled, true);
        this.reminderEnabled = valueOrDefault(reminderEnabled, true);
        this.quietHoursEnabled = valueOrDefault(quietHoursEnabled, false);
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.timezone = (timezone == null || timezone.isBlank()) ? "Asia/Seoul" : timezone;
        validateQuietHours();
        this.createdAt = nowUtc();
        this.updatedAt = nowUtc();
    }

    public static NotificationPreference createDefault(Long userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .build();
    }

    public void update(Boolean pushEnabled,
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
                       String timezone) {
        if (pushEnabled != null) {
            this.pushEnabled = pushEnabled;
        }
        if (inAppEnabled != null) {
            this.inAppEnabled = inAppEnabled;
        }
        if (matchRequestEnabled != null) {
            this.matchRequestEnabled = matchRequestEnabled;
        }
        if (matchResultEnabled != null) {
            this.matchResultEnabled = matchResultEnabled;
        }
        if (groupMatchingEnabled != null) {
            this.groupMatchingEnabled = groupMatchingEnabled;
        }
        if (chatMessageEnabled != null) {
            this.chatMessageEnabled = chatMessageEnabled;
        }
        if (milestoneEnabled != null) {
            this.milestoneEnabled = milestoneEnabled;
        }
        if (reminderEnabled != null) {
            this.reminderEnabled = reminderEnabled;
        }
        if (quietHoursEnabled != null) {
            this.quietHoursEnabled = quietHoursEnabled;
        }
        if (quietHoursStart != null) {
            this.quietHoursStart = quietHoursStart;
        }
        if (quietHoursEnd != null) {
            this.quietHoursEnd = quietHoursEnd;
        }
        if (timezone != null && !timezone.isBlank()) {
            this.timezone = timezone;
        }
        validateQuietHours();
        touch();
    }

    private void validateQuietHours() {
        if (Boolean.TRUE.equals(this.quietHoursEnabled)) {
            if (this.quietHoursStart == null || this.quietHoursEnd == null) {
                throw new IllegalArgumentException("Quiet hours start and end are required.");
            }
            return;
        }
        if (this.quietHoursStart == null && this.quietHoursEnd == null) {
            return;
        }
        if (this.quietHoursStart == null || this.quietHoursEnd == null) {
            throw new IllegalArgumentException("Quiet hours start and end must be configured together.");
        }
    }

    private void touch() {
        this.updatedAt = nowUtc();
    }

    private static boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }
}
