package univ.airconnect.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import univ.airconnect.notification.domain.PushEventType;
import univ.airconnect.notification.domain.entity.PushEvent;

import java.time.LocalDateTime;

/**
 * 클라이언트 푸시 이벤트를 저장한 뒤 반환하는 응답 모델이다.
 */
@Getter
@AllArgsConstructor
public class PushEventResponse {

    private Long pushEventId;

    private String notificationId;

    private String providerMessageId;

    private PushEventType eventType;

    private String deviceId;

    private LocalDateTime occurredAt;

    private LocalDateTime storedAt;

    public static PushEventResponse from(PushEvent pushEvent) {
        return new PushEventResponse(
                pushEvent.getId(),
                String.valueOf(pushEvent.getNotificationId()),
                pushEvent.getProviderMessageId(),
                pushEvent.getEventType(),
                pushEvent.getDeviceId(),
                pushEvent.getOccurredAt(),
                pushEvent.getCreatedAt()
        );
    }
}
