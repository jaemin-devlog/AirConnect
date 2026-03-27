package univ.airconnect.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 알림함 API가 반환하는 커서 기반 페이지 응답 모델이다.
 */
@Getter
@AllArgsConstructor
public class NotificationListResponse {

    private int requestedSize;

    private long unreadCount;

    private boolean hasNext;

    private Long nextCursorId;

    private int count;

    private List<NotificationItemResponse> items;
}
