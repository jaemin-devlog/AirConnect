package univ.airconnect.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.notification.domain.NotificationCategory;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime readAt;
    private Boolean deleted;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", timezone = "UTC")
    private OffsetDateTime createdAt;

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
                .readAt(toOffset(notification.getReadAt()))
                .deleted(notification.isDeleted())
                .createdAt(toOffset(notification.getCreatedAt()))
                .build();
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC);
    }
}
