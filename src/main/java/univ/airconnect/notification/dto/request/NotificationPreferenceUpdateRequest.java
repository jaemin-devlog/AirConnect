package univ.airconnect.notification.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 사용자별 알림 설정 부분 수정 요청 모델이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceUpdateRequest {

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
}
