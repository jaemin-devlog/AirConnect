package univ.airconnect.ticket.domain.entity;

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
        name = "festival_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_festival_coupon_code", columnNames = "code"),
        indexes = @Index(name = "idx_festival_coupon_redeemed_by", columnList = "redeemed_by_user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalCoupon {

    public static final int DEFAULT_TICKET_AMOUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6, updatable = false)
    private String code;

    @Column(name = "ticket_amount", nullable = false, updatable = false)
    private Integer ticketAmount;

    @Column(name = "redeemed_by_user_id")
    private Long redeemedByUserId;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FestivalCoupon(String code, int ticketAmount) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("쿠폰 코드는 숫자 6자리여야 합니다.");
        }
        if (ticketAmount <= 0) {
            throw new IllegalArgumentException("지급 티켓 수는 1개 이상이어야 합니다.");
        }
        this.code = code;
        this.ticketAmount = ticketAmount;
        this.createdAt = LocalDateTime.now();
    }

    public static FestivalCoupon issue(String code) {
        return new FestivalCoupon(code, DEFAULT_TICKET_AMOUNT);
    }

    public boolean isRedeemed() {
        return redeemedAt != null;
    }

    public void redeem(Long userId, LocalDateTime redeemedAt) {
        if (isRedeemed()) {
            throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        }
        if (userId == null || redeemedAt == null) {
            throw new IllegalArgumentException("사용자와 사용 시각은 필수입니다.");
        }
        this.redeemedByUserId = userId;
        this.redeemedAt = redeemedAt;
    }
}
