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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.notification.domain.NotificationDeliveryStatus;
import univ.airconnect.notification.domain.PushProvider;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_outbox_notification_device",
                        columnNames = {"notification_id", "push_device_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_outbox_status_next_attempt",
                        columnList = "status, next_attempt_at"
                ),
                @Index(name = "idx_notification_outbox_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_device_id", nullable = false)
    private Long pushDeviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "target_token", nullable = false, length = 512)
    private String targetToken;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "data_json", columnDefinition = "JSON", nullable = false)
    private String dataJson;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private NotificationOutbox(Long notificationId,
                               Long userId,
                               Long pushDeviceId,
                               PushProvider provider,
                               String targetToken,
                               String title,
                               String body,
                               String dataJson,
                               LocalDateTime nextAttemptAt) {
        validate(notificationId, userId, pushDeviceId, provider, targetToken, title, body, dataJson, nextAttemptAt);
        this.notificationId = notificationId;
        this.userId = userId;
        this.pushDeviceId = pushDeviceId;
        this.provider = provider;
        this.status = NotificationDeliveryStatus.PENDING;
        this.targetToken = targetToken;
        this.title = title;
        this.body = body;
        this.dataJson = dataJson;
        this.attemptCount = 0;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = nowUtc();
        this.updatedAt = nowUtc();
    }

    public static NotificationOutbox create(Long notificationId,
                                            Long userId,
                                            Long pushDeviceId,
                                            PushProvider provider,
                                            String targetToken,
                                            String title,
                                            String body,
                                            String dataJson,
                                            LocalDateTime nextAttemptAt) {
        return NotificationOutbox.builder()
                .notificationId(notificationId)
                .userId(userId)
                .pushDeviceId(pushDeviceId)
                .provider(provider)
                .targetToken(targetToken)
                .title(title)
                .body(body)
                .dataJson(dataJson)
                .nextAttemptAt(nextAttemptAt)
                .build();
    }

    public void coalesceToLatest(Long notificationId,
                                 String targetToken,
                                 String title,
                                 String body,
                                 String dataJson,
                                 LocalDateTime nextAttemptAt) {
        if (this.status != NotificationDeliveryStatus.PENDING) {
            throw new IllegalStateException("Only pending outboxes can be coalesced.");
        }
        validate(notificationId, this.userId, this.pushDeviceId, this.provider, targetToken, title, body, dataJson, nextAttemptAt);
        this.notificationId = notificationId;
        this.targetToken = targetToken;
        this.title = title;
        this.body = body;
        this.dataJson = dataJson;
        this.nextAttemptAt = nextAttemptAt;
        touch();
    }

    public void claim() {
        if (this.status != NotificationDeliveryStatus.PENDING) {
            throw new IllegalStateException("Only pending outboxes can be claimed.");
        }
        this.status = NotificationDeliveryStatus.PROCESSING;
        this.claimedAt = nowUtc();
        touch();
    }

    public void markSent(String providerMessageId) {
        this.status = NotificationDeliveryStatus.SENT;
        this.sentAt = nowUtc();
        this.providerMessageId = providerMessageId;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.claimedAt = null;
        touch();
    }

    public void markRetry(String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("Retry time is required.");
        }
        this.status = NotificationDeliveryStatus.PENDING;
        this.attemptCount = this.attemptCount + 1;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.attemptCount = this.attemptCount + 1;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    public void defer(String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("Deferred time is required.");
        }
        this.status = NotificationDeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    public void markSkipped(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.SKIPPED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    private void touch() {
        this.updatedAt = nowUtc();
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    private void validate(Long notificationId,
                          Long userId,
                          Long pushDeviceId,
                          PushProvider provider,
                          String targetToken,
                          String title,
                          String body,
                          String dataJson,
                          LocalDateTime nextAttemptAt) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification id is required.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        if (pushDeviceId == null) {
            throw new IllegalArgumentException("Push device id is required.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Push provider is required.");
        }
        if (targetToken == null || targetToken.isBlank()) {
            throw new IllegalArgumentException("Target token is required.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title is required.");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Notification body is required.");
        }
        if (dataJson == null || dataJson.isBlank()) {
            throw new IllegalArgumentException("Notification dataJson is required.");
        }
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("Next attempt time is required.");
        }
    }
}
