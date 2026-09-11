package univ.airconnect.referral.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "referral_redemptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referral_referred_user", columnNames = "referred_user_id"),
                @UniqueConstraint(name = "uk_referral_user_pair", columnNames = {"pair_low_user_id", "pair_high_user_id"})
        },
        indexes = @Index(name = "idx_referral_referrer_created", columnList = "referrer_user_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_user_id", nullable = false, updatable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id", nullable = false, updatable = false)
    private Long referredUserId;

    @Column(name = "referral_code", nullable = false, length = 6, updatable = false)
    private String referralCode;

    @Column(name = "pair_low_user_id", nullable = false, updatable = false)
    private Long pairLowUserId;

    @Column(name = "pair_high_user_id", nullable = false, updatable = false)
    private Long pairHighUserId;

    @Column(name = "reward_tickets", nullable = false, updatable = false)
    private Integer rewardTickets;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ReferralRedemption(Long referrerUserId,
                               Long referredUserId,
                               String referralCode,
                               int rewardTickets) {
        if (referrerUserId == null || referredUserId == null || referrerUserId.equals(referredUserId)) {
            throw new IllegalArgumentException("추천인과 추천받은 사용자가 올바르지 않습니다.");
        }
        if (referralCode == null || rewardTickets <= 0) {
            throw new IllegalArgumentException("추천 보상 정보가 올바르지 않습니다.");
        }
        this.referrerUserId = referrerUserId;
        this.referredUserId = referredUserId;
        this.referralCode = referralCode;
        this.pairLowUserId = Math.min(referrerUserId, referredUserId);
        this.pairHighUserId = Math.max(referrerUserId, referredUserId);
        this.rewardTickets = rewardTickets;
        this.createdAt = LocalDateTime.now();
    }

    public static ReferralRedemption create(Long referrerUserId,
                                            Long referredUserId,
                                            String referralCode,
                                            int rewardTickets) {
        return new ReferralRedemption(referrerUserId, referredUserId, referralCode, rewardTickets);
    }
}
