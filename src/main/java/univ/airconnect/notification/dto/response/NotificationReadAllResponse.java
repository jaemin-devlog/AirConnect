package univ.airconnect.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 알림을 읽음 처리한 뒤 반환하는 응답 모델이다.
 */
@Getter
@AllArgsConstructor
public class NotificationReadAllResponse {

    private int readCount;

    private long unreadCount;
}
