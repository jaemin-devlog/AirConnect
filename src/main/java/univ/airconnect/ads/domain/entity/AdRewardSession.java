package univ.airconnect.ads.domain.entity;

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
import univ.airconnect.ads.domain.AdRewardSessionStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ad_reward_sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ad_reward_sessions_session_key", columnNames = "session_key"),
                @UniqueConstraint(name = "uk_ad_reward_sessions_transaction_id", columnNames = "transaction_id")
        },
        indexes = {
                @Index(name = "idx_ad_reward_sessions_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_ad_reward_sessions_status_expires", columnList = "status, expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdRewardSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, length = 80)
    private String sessionKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reward_amount", nullable = false)
    private Integer rewardAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdRewardSessionStatus status;

    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    @Builder
    private AdRewardSession(String sessionKey,
                            Long userId,
                            Integer rewardAmount,
                            AdRewardSessionStatus status,
                            LocalDateTime expiresAt) {
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.rewardAmount = rewardAmount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static AdRewardSession createReady(String sessionKey, Long userId, int rewardAmount, LocalDateTime expiresAt) {
        return AdRewardSession.builder()
                .sessionKey(sessionKey)
                .userId(userId)
                .rewardAmount(rewardAmount)
                .status(AdRewardSessionStatus.READY)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public void markExpired() {
        this.status = AdRewardSessionStatus.EXPIRED;
    }

    public void markRewarded(String transactionId) {
        this.status = AdRewardSessionStatus.REWARDED;
        this.transactionId = transactionId;
        this.rewardedAt = LocalDateTime.now();
    }

    public boolean isReady() {
        return this.status == AdRewardSessionStatus.READY;
    }

    public boolean isRewarded() {
        return this.status == AdRewardSessionStatus.REWARDED;
    }
}

