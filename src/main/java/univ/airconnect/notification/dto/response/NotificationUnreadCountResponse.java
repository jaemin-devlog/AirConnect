package univ.airconnect.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 앱 배지와 탭 표시용 미읽음 개수 응답 모델이다.
 */
@Getter
@AllArgsConstructor
public class NotificationUnreadCountResponse {

    private long unreadCount;
}
