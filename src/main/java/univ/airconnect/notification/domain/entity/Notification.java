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
import univ.airconnect.notification.domain.NotificationCategory;
import univ.airconnect.notification.domain.NotificationType;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 사용자 알림 센터에 저장되는 알림 엔티티다.
 *
 * <p>앱 내 알림 목록의 원본 데이터이며, 푸시 발송 성공 여부와 무관하게 먼저 저장된다.
 * 한 명의 수신자 기준으로 한 행이 생성된다.</p>
 *
 * <p>category는 외부에서 임의로 받지 않고 type에서 유도한다.
 * 타입과 카테고리가 어긋난 잘못된 저장을 막기 위한 설계다.</p>
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notifications_user_dedupe", columnNames = {"user_id", "dedupe_key"})
        },
        indexes = {
                @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_notifications_user_read", columnList = "user_id, read_at, created_at"),
                @Index(name = "idx_notifications_type_created", columnList = "type, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    /** 알림 PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 알림을 받는 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 구체적인 비즈니스 이벤트 타입 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    /** 기능 영역 기준 카테고리 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    /** 알림 목록/푸시 제목 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 알림 목록/푸시 본문 */
    @Column(nullable = false, length = 500)
    private String body;

    /** 앱 내 이동용 deep link */
    @Column(length = 255)
    private String deeplink;

    /** 행위를 발생시킨 사용자 ID. 시스템 알림이면 null 가능 */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /** 썸네일 또는 보조 이미지 URL */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 클라이언트가 상세 처리를 위해 사용하는 JSON payload */
    @Column(name = "payload_json", columnDefinition = "JSON", nullable = false)
    private String payloadJson;

    /** 동일 이벤트 중복 저장 방지를 위한 키 */
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    /** 사용자가 읽은 시각 */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** 소프트 삭제 시각 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(Long userId,
                         NotificationType type,
                         String title,
                         String body,
                         String deeplink,
                         Long actorUserId,
                         String imageUrl,
                         String payloadJson,
                         String dedupeKey) {
        validate(userId, type, title, body, payloadJson, dedupeKey);
        this.userId = userId;
        this.type = type;
        this.category = type.getCategory();
        this.title = title;
        this.body = body;
        this.deeplink = deeplink;
        this.actorUserId = actorUserId;
        this.imageUrl = imageUrl;
        this.payloadJson = payloadJson;
        this.dedupeKey = dedupeKey;
        this.createdAt = LocalDateTime.now(Clock.systemUTC());
    }

    /**
     * 읽지 않은 새 알림을 생성한다.
     */
    public static Notification create(Long userId,
                                      NotificationType type,
                                      String title,
                                      String body,
                                      String deeplink,
                                      Long actorUserId,
                                      String imageUrl,
                                      String payloadJson,
                                      String dedupeKey) {
        return Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .deeplink(deeplink)
                .actorUserId(actorUserId)
                .imageUrl(imageUrl)
                .payloadJson(payloadJson)
                .dedupeKey(dedupeKey)
                .build();
    }

    /**
     * 알림을 읽음 처리한다.
     * 이미 읽은 알림이면 아무 동작도 하지 않는다.
     */
    public void markRead() {
        if (this.readAt != null) {
            return;
        }
        this.readAt = LocalDateTime.now(Clock.systemUTC());
    }

    /**
     * 알림을 소프트 삭제한다.
     * 실제 행 삭제 대신 deletedAt만 기록한다.
     */
    public void softDelete() {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = LocalDateTime.now(Clock.systemUTC());
    }

    /** 알림이 읽힘 상태인지 반환한다. */
    public boolean isRead() {
        return this.readAt != null;
    }

    /** 알림이 삭제 상태인지 반환한다. */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    /**
     * 생성 시 필수 값 검증을 수행한다.
     */
    private void validate(Long userId,
                          NotificationType type,
                          String title,
                          String body,
                          String payloadJson,
                          String dedupeKey) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("알림 유형은 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("알림 제목은 필수입니다.");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("알림 본문은 필수입니다.");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("알림 payloadJson은 필수입니다.");
        }
        if (type.requiresDedupeKey() && (dedupeKey == null || dedupeKey.isBlank())) {
            throw new IllegalArgumentException(type.name() + " 유형에는 dedupeKey가 필요합니다.");
        }
    }
}
