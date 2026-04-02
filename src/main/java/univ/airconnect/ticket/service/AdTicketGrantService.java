package univ.airconnect.ticket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.ticket.repository.AdTicketLedgerRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@Slf4j
public class AdTicketGrantService {

    private final UserRepository userRepository;
    private final AdTicketLedgerRepository adTicketLedgerRepository;

    public AdTicketGrantService(UserRepository userRepository,
                                AdTicketLedgerRepository adTicketLedgerRepository) {
        this.userRepository = userRepository;
        this.adTicketLedgerRepository = adTicketLedgerRepository;
    }

    @Transactional
    public GrantResult grantFromAdReward(Long userId, int amount, Long sessionId) {
        String refId = String.valueOf(sessionId);
        TicketLedger existing = adTicketLedgerRepository
                .findByRefTypeAndRefId(LedgerRefType.AD_REWARD_SESSION, refId)
                .orElse(null);
        if (existing != null) {
            return GrantResult.alreadyGranted(existing.getBeforeAmount(), existing.getAfterAmount(), existing.ledgerExternalId());
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AdsException(AdsErrorCode.AD_REWARD_INVALID_SESSION));

        int before = user.getTickets();
        user.addTickets(amount);
        int after = user.getTickets();

        try {
            TicketLedger ledger = adTicketLedgerRepository.save(
                    TicketLedger.grantForAdReward(user.getId(), amount, before, after, refId)
            );
            log.info("Ad reward ticket ledger saved. userId={}, sessionId={}, amount={}, ledgerId={}",
                    userId, sessionId, amount, ledger.ledgerExternalId());
            return GrantResult.granted(before, after, ledger.ledgerExternalId());
        } catch (DataIntegrityViolationException e) {
            TicketLedger recovered = adTicketLedgerRepository
                    .findByRefTypeAndRefId(LedgerRefType.AD_REWARD_SESSION, refId)
                    .orElseThrow(() -> new AdsException(AdsErrorCode.AD_REWARD_DUPLICATE_REQUEST));
            return GrantResult.alreadyGranted(
                    recovered.getBeforeAmount(),
                    recovered.getAfterAmount(),
                    recovered.ledgerExternalId()
            );
        }
    }

    public record GrantResult(boolean granted, int beforeTickets, int afterTickets, String ledgerExternalId) {

        public static GrantResult granted(int beforeTickets, int afterTickets, String ledgerExternalId) {
            return new GrantResult(true, beforeTickets, afterTickets, ledgerExternalId);
        }

        public static GrantResult alreadyGranted(int beforeTickets, int afterTickets, String ledgerExternalId) {
            return new GrantResult(false, beforeTickets, afterTickets, ledgerExternalId);
        }
    }
}

