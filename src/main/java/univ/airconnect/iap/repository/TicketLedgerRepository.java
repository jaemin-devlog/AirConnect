package univ.airconnect.iap.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;

import java.util.Optional;

public interface TicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    Optional<TicketLedger> findByRefTypeAndRefId(LedgerRefType refType, String refId);

    Page<TicketLedger> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN tl.changeAmount > 0 THEN tl.changeAmount ELSE 0 END), 0)
        FROM TicketLedger tl
    """)
    long sumGrantedTickets();

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN tl.changeAmount < 0 THEN -tl.changeAmount ELSE 0 END), 0)
        FROM TicketLedger tl
    """)
    long sumConsumedTickets();
}
