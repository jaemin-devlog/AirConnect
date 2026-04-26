package univ.airconnect.iap.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;

import java.util.List;
import java.util.Optional;

public interface TicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    Optional<TicketLedger> findByRefTypeAndRefId(LedgerRefType refType, String refId);

    Page<TicketLedger> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT tl
        FROM TicketLedger tl
        WHERE tl.userId = :userId
          AND tl.changeAmount < 0
        ORDER BY tl.createdAt DESC, tl.id DESC
    """)
    List<TicketLedger> findTop20UsageHistoryByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN tl.changeAmount > 0 THEN tl.changeAmount ELSE 0 END), 0)
        FROM TicketLedger tl
        WHERE tl.refType <> univ.airconnect.iap.domain.LedgerRefType.ADMIN_ADJUSTMENT
    """)
    long sumGrantedTickets();

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN tl.changeAmount < 0 THEN -tl.changeAmount ELSE 0 END), 0)
        FROM TicketLedger tl
        WHERE tl.refType <> univ.airconnect.iap.domain.LedgerRefType.ADMIN_ADJUSTMENT
    """)
    long sumConsumedTickets();
}
