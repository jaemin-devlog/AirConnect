package univ.airconnect.referral.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "referral_codes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referral_code_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_referral_code_code", columnNames = "code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 6, updatable = false)
    private String code;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ReferralCode(Long userId, String code) {
        if (userId == null || code == null || !code.matches("(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{6}")) {
            throw new IllegalArgumentException("추천인 코드 정보가 올바르지 않습니다.");
        }
        this.userId = userId;
        this.code = code;
        this.createdAt = LocalDateTime.now();
    }

    public static ReferralCode issue(Long userId, String code) {
        return new ReferralCode(userId, code);
    }
}
