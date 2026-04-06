package univ.airconnect.moderation.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_blocks_blocker_blocked", columnNames = {"blocker_user_id", "blocked_user_id"})
        },
        indexes = {
                @Index(name = "idx_user_blocks_blocker_created", columnList = "blocker_user_id, created_at"),
                @Index(name = "idx_user_blocks_blocked_created", columnList = "blocked_user_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_user_id", nullable = false)
    private Long blockerUserId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserBlock(Long blockerUserId,
                      Long blockedUserId) {
        this.blockerUserId = blockerUserId;
        this.blockedUserId = blockedUserId;
        this.createdAt = LocalDateTime.now();
    }

    public static UserBlock create(Long blockerUserId, Long blockedUserId) {
        return UserBlock.builder()
                .blockerUserId(blockerUserId)
                .blockedUserId(blockedUserId)
                .build();
    }
}
