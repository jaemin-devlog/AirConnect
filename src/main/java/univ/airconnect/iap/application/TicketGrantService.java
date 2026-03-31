package univ.airconnect.iap.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@Slf4j
public class TicketGrantService {

    private final UserRepository userRepository;
    private final TicketLedgerRepository ticketLedgerRepository;

    public TicketGrantService(UserRepository userRepository,
                              TicketLedgerRepository ticketLedgerRepository) {
        this.userRepository = userRepository;
        this.ticketLedgerRepository = ticketLedgerRepository;
    }

    @Transactional
    public TicketGrantResult grantTickets(IapOrder order, int ticketAmount) {
        log.info("IAP ticket grant started. orderId={}, userId={}, ticketAmount={}",
                order.getId(), order.getUserId(), ticketAmount);
        String refId = String.valueOf(order.getId());
        TicketLedger existing = ticketLedgerRepository.findByRefTypeAndRefId(univ.airconnect.iap.domain.LedgerRefType.IAP_ORDER, refId)
                .orElse(null);
        if (existing != null) {
            log.info("IAP ticket grant skipped (already granted). orderId={}, ledgerId={}",
                    order.getId(), existing.ledgerExternalId());
            return new TicketGrantResult(existing.getBeforeAmount(), existing.getAfterAmount(), existing.ledgerExternalId());
        }

        User user = userRepository.findByIdForUpdate(order.getUserId())
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_UNAUTHORIZED));

        int before = user.getTickets();
        user.addTickets(ticketAmount);
        int after = user.getTickets();
        log.info("IAP ticket balance updated. orderId={}, userId={}, before={}, after={}",
                order.getId(), user.getId(), before, after);

        try {
            TicketLedger ledger = ticketLedgerRepository.save(TicketLedger.grantForIap(user.getId(), ticketAmount, before, after, refId));
            log.info("IAP ticket ledger saved. orderId={}, ledgerId={}", order.getId(), ledger.ledgerExternalId());
            return new TicketGrantResult(before, after, ledger.ledgerExternalId());
        } catch (DataIntegrityViolationException e) {
            log.warn("IAP ticket ledger unique conflict. orderId={}", order.getId());
            TicketLedger ledger = ticketLedgerRepository.findByRefTypeAndRefId(univ.airconnect.iap.domain.LedgerRefType.IAP_ORDER, refId)
                    .orElseThrow(() -> new IapException(IapErrorCode.IAP_DUPLICATE_REQUEST));
            log.info("IAP ticket ledger recovered after conflict. orderId={}, ledgerId={}",
                    order.getId(), ledger.ledgerExternalId());
            return new TicketGrantResult(ledger.getBeforeAmount(), ledger.getAfterAmount(), ledger.ledgerExternalId());
        }
    }

    public record TicketGrantResult(int beforeTickets, int afterTickets, String ledgerExternalId) {
    }
}

