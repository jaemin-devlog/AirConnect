package univ.airconnect.ads.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.ads.domain.entity.AdRewardSession;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AdRewardSessionRepository extends JpaRepository<AdRewardSession, Long> {

    Optional<AdRewardSession> findBySessionKey(String sessionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AdRewardSession s WHERE s.sessionKey = :sessionKey")
    Optional<AdRewardSession> findBySessionKeyForUpdate(@Param("sessionKey") String sessionKey);

    boolean existsByTransactionId(String transactionId);

    @Query("""
            SELECT COUNT(s)
            FROM AdRewardSession s
            WHERE s.userId = :userId
              AND s.status = univ.airconnect.ads.domain.AdRewardSessionStatus.REWARDED
              AND s.rewardedAt >= :start
              AND s.rewardedAt < :end
            """)
    long countRewardedToday(@Param("userId") Long userId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);
}


