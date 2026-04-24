package univ.airconnect.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 사용자별 알림 설정 엔티티다.
 *
 * <p>전체 on/off 뿐 아니라 카테고리별 허용 여부와 방해금지 시간대까지 관리한다.</p>
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    /** 사용자 ID를 PK로 사용한다. 사용자당 한 행만 존재한다. */
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 전체 푸시 허용 여부 */
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled;

    /** 앱 내 알림 센터 저장/실시간 노출 허용 여부 */
    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled;

    /** 1:1 매칭 요청 알림 허용 여부 */
    @Column(name = "match_request_enabled", nullable = false)
    private Boolean matchRequestEnabled;

    /** 1:1 매칭 결과 알림 허용 여부 */
    @Column(name = "match_result_enabled", nullable = false)
    private Boolean matchResultEnabled;

    /** 그룹 매칭 관련 알림 허용 여부 */
    @Column(name = "group_matching_enabled", nullable = false)
    private Boolean groupMatchingEnabled;

    /** 채팅 메시지 푸시 허용 여부 */
    @Column(name = "chat_message_enabled", nullable = false)
    private Boolean chatMessageEnabled;

    /** 마일스톤/보상 알림 허용 여부 */
    @Column(name = "milestone_enabled", nullable = false)
    private Boolean milestoneEnabled;

    /** 일정/리마인드 알림 허용 여부 */
    @Column(name = "reminder_enabled", nullable = false)
    private Boolean reminderEnabled;

    /** 방해금지 시간 사용 여부 */
    @Column(name = "quiet_hours_enabled", nullable = false)
    private Boolean quietHoursEnabled;

    /** 방해금지 시작 시각 */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    /** 방해금지 종료 시각 */
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    /** 사용자의 기준 시간대 */
    @Column(nullable = false, length = 50)
    private String timezone;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
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
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 기본값으로 켜진 알림 설정을 생성한다.
     */
    public static NotificationPreference createDefault(Long userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .build();
    }

    /**
     * 부분 업데이트 방식으로 설정을 변경한다.
     * null 값은 기존 값을 유지한다.
     */
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

    /**
     * 방해금지 시간 설정의 일관성을 검증한다.
     */
    private void validateQuietHours() {
        if (Boolean.TRUE.equals(this.quietHoursEnabled)) {
            if (this.quietHoursStart == null || this.quietHoursEnd == null) {
                throw new IllegalArgumentException("방해 금지 시간의 시작과 종료 시각은 모두 필요합니다.");
            }
            return;
        }
        if (this.quietHoursStart == null && this.quietHoursEnd == null) {
            return;
        }
        if (this.quietHoursStart == null || this.quietHoursEnd == null) {
            throw new IllegalArgumentException("방해 금지 시간의 시작과 종료 시각은 함께 설정해야 합니다.");
        }
    }

    /** updatedAt 갱신용 공통 메서드 */
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** nullable Boolean 값을 기본값으로 보정한다. */
    private static boolean valueOrDefault(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
}
