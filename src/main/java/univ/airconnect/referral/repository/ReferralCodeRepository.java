package univ.airconnect.referral.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.referral.domain.entity.ReferralCode;

import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByUserId(Long userId);
    Optional<ReferralCode> findByCode(String code);
    boolean existsByCode(String code);
}
