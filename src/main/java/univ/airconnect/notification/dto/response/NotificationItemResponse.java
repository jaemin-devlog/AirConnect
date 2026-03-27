package univ.airconnect.notification.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.notification.domain.NotificationCategory;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;

import java.time.LocalDateTime;

/**
 * 모바일 클라이언트에 반환하는 알림 한 건의 응답 모델이다.
 */
@Getter
@Builder
public class NotificationItemResponse {

    private Long notificationId;
    private Long userId;
    private NotificationType type;
    private NotificationCategory category;
    private String title;
    private String body;
    private String deeplink;
    private Long actorUserId;
    private String imageUrl;
    private Object payload;
    private Boolean read;
    private LocalDateTime readAt;
    private Boolean deleted;
    private LocalDateTime createdAt;

    public static NotificationItemResponse from(Notification notification, Object payload) {
        return NotificationItemResponse.builder()
                .notificationId(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .category(notification.getCategory())
                .title(notification.getTitle())
                .body(notification.getBody())
                .deeplink(notification.getDeeplink())
                .actorUserId(notification.getActorUserId())
                .imageUrl(notification.getImageUrl())
                .payload(payload)
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .deleted(notification.isDeleted())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
