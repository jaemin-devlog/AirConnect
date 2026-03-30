package univ.airconnect.iap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;

import java.util.Optional;

public interface TicketLedgerRepository extends JpaRepository<TicketLedger, Long> {

    Optional<TicketLedger> findByRefTypeAndRefId(LedgerRefType refType, String refId);
}

