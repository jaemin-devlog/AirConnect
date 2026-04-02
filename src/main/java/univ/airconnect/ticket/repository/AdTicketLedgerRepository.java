package univ.airconnect.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;

import java.util.Optional;

public interface AdTicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    Optional<TicketLedger> findByRefTypeAndRefId(LedgerRefType refType, String refId);
}

