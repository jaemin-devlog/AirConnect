package univ.airconnect.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface AdTicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    Optional<TicketLedger> findByRefTypeAndRefId(LedgerRefType refType, String refId);

    // A current (locking) read also observes callbacks committed after a MySQL repeatable-read snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM TicketLedger l WHERE l.userId = :userId
              AND l.refType = univ.airconnect.iap.domain.LedgerRefType.AD_REWARD_SESSION
              AND l.createdAt >= :start AND l.createdAt < :end
            """)
    List<TicketLedger> findDailyRewardsForUpdate(@Param("userId") Long userId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);
}

