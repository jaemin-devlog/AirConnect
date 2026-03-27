package univ.airconnect.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.notification.domain.PushEventType;

import java.time.LocalDateTime;

/**
 * 클라이언트가 보고한 푸시 수신/열람 이벤트를 저장한다.
 */
@Entity
@Table(
        name = "push_events",
        indexes = {
                @Index(name = "idx_push_events_notification", columnList = "notification_id, occurred_at"),
                @Index(name = "idx_push_events_user_device", columnList = "user_id, device_id, occurred_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PushEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PushEvent(Long userId,
                      Long notificationId,
                      String providerMessageId,
                      PushEventType eventType,
                      LocalDateTime occurredAt,
                      String deviceId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId is required");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        this.userId = userId;
        this.notificationId = notificationId;
        this.providerMessageId = providerMessageId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.deviceId = deviceId;
        this.createdAt = LocalDateTime.now();
    }

    public static PushEvent create(Long userId,
                                   Long notificationId,
                                   String providerMessageId,
                                   PushEventType eventType,
                                   LocalDateTime occurredAt,
                                   String deviceId) {
        return PushEvent.builder()
                .userId(userId)
                .notificationId(notificationId)
                .providerMessageId(providerMessageId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .deviceId(deviceId)
                .build();
    }
}
