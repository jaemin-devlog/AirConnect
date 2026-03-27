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

import java.time.LocalDateTime;

/**
 * 외부 푸시 발송을 위한 작업 큐 엔티티다.
 *
 * <p>알림 저장과 실제 발송을 분리하기 위해 사용한다.
 * 하나의 notification + device 조합마다 한 행이 생성된다.</p>
 *
 * <p>실제 중복 점유 방지는 조회 계층에서 조건부 UPDATE 또는
 * SELECT ... FOR UPDATE SKIP LOCKED 같은 원자적 claim 방식으로 마무리해야 한다.
 * 이 엔티티는 그 흐름을 표현하기 위한 PROCESSING 상태를 포함한다.</p>
 */
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

    /** outbox PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 알림 ID */
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    /** 수신 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 발송 대상 디바이스 ID */
    @Column(name = "push_device_id", nullable = false)
    private Long pushDeviceId;

    /** 실제 발송 프로바이더 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushProvider provider;

    /** 발송 처리 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    /** 프로바이더에 전달할 대상 토큰 */
    @Column(name = "target_token", nullable = false, length = 512)
    private String targetToken;

    /** 푸시 제목 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 푸시 본문 */
    @Column(nullable = false, length = 500)
    private String body;

    /** 프로바이더 data payload 원본 JSON */
    @Column(name = "data_json", columnDefinition = "JSON", nullable = false)
    private String dataJson;

    /** 누적 시도 횟수 */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    /** 다음 발송 시도 예정 시각 */
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    /** 워커가 현재 작업을 점유한 시각 */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** 실제 발송 완료 시각 */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 외부 프로바이더가 반환한 메시지 ID */
    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    /** 마지막 실패 코드 */
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    /** 마지막 실패 메시지 */
    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 */
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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 새 발송 대기 작업을 생성한다.
     */
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

    /**
     * 워커가 현재 작업을 집어간 시점을 기록한다.
     */
    public void claim() {
        if (this.status != NotificationDeliveryStatus.PENDING) {
            throw new IllegalStateException("only pending outbox can be claimed");
        }
        this.status = NotificationDeliveryStatus.PROCESSING;
        this.claimedAt = LocalDateTime.now();
        touch();
    }

    /**
     * 발송 성공 상태로 전이한다.
     */
    public void markSent(String providerMessageId) {
        this.status = NotificationDeliveryStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.providerMessageId = providerMessageId;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.claimedAt = null;
        touch();
    }

    /**
     * 재시도 가능한 실패를 기록하고 다시 대기 상태로 되돌린다.
     */
    public void markRetry(String errorCode, String errorMessage, LocalDateTime nextAttemptAt) {
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt is required for retry");
        }
        this.status = NotificationDeliveryStatus.PENDING;
        this.attemptCount = this.attemptCount + 1;
        this.nextAttemptAt = nextAttemptAt;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    /**
     * 더 이상 재시도하지 않는 최종 실패 상태로 기록한다.
     */
    public void markFailed(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.FAILED;
        this.attemptCount = this.attemptCount + 1;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    /**
     * 정책상 발송하지 않기로 한 작업을 SKIPPED 상태로 기록한다.
     */
    public void markSkipped(String errorCode, String errorMessage) {
        this.status = NotificationDeliveryStatus.SKIPPED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.claimedAt = null;
        touch();
    }

    /** updatedAt 갱신용 공통 메서드 */
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 생성 시 필수 값 검증을 수행한다.
     */
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
            throw new IllegalArgumentException("notificationId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (pushDeviceId == null) {
            throw new IllegalArgumentException("pushDeviceId is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (targetToken == null || targetToken.isBlank()) {
            throw new IllegalArgumentException("targetToken is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        if (dataJson == null || dataJson.isBlank()) {
            throw new IllegalArgumentException("dataJson is required");
        }
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt is required");
        }
    }
}
