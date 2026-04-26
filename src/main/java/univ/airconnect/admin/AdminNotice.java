package univ.airconnect.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "admin_notices",
        indexes = {
                @Index(name = "idx_admin_notices_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(length = 255)
    private String deeplink;

    @Column(name = "active_users_only", nullable = false)
    private boolean activeUsersOnly;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminNotice(Long createdByUserId,
                        String title,
                        String body,
                        String deeplink,
                        boolean activeUsersOnly,
                        int recipientCount) {
        this.createdByUserId = createdByUserId;
        this.title = title;
        this.body = body;
        this.deeplink = deeplink;
        this.activeUsersOnly = activeUsersOnly;
        this.recipientCount = recipientCount;
        this.createdAt = LocalDateTime.now();
    }

    public static AdminNotice create(Long createdByUserId,
                                     String title,
                                     String body,
                                     String deeplink,
                                     boolean activeUsersOnly,
                                     int recipientCount) {
        return AdminNotice.builder()
                .createdByUserId(createdByUserId)
                .title(title)
                .body(body)
                .deeplink(deeplink)
                .activeUsersOnly(activeUsersOnly)
                .recipientCount(recipientCount)
                .build();
    }
}
