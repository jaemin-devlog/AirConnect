package univ.airconnect.referral.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.referral.domain.entity.ReferralRedemption;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ReferralRedemptionRepository extends JpaRepository<ReferralRedemption, Long> {
    Optional<ReferralRedemption> findByReferredUserId(Long referredUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReferralRedemption r WHERE r.referredUserId = :referredUserId")
    Optional<ReferralRedemption> findByReferredUserIdForUpdate(@Param("referredUserId") Long referredUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM ReferralRedemption r
        WHERE r.pairLowUserId = :pairLowUserId
          AND r.pairHighUserId = :pairHighUserId
    """)
    Optional<ReferralRedemption> findByPairForUpdate(@Param("pairLowUserId") Long pairLowUserId,
                                                     @Param("pairHighUserId") Long pairHighUserId);

    long countByReferrerUserId(Long referrerUserId);
}
