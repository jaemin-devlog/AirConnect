package univ.airconnect.iap.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import univ.airconnect.iap.domain.entity.TicketLedger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TicketLedgerRepositoryTest {

    @Autowired
    private TicketLedgerRepository ticketLedgerRepository;

    @Test
    void sumsExcludeAdminAdjustmentsFromGrantedAndConsumedTotals() {
        ticketLedgerRepository.save(TicketLedger.grantForIap(1L, 10, 0, 10, "iap-1"));
        ticketLedgerRepository.save(TicketLedger.grantForAdReward(1L, 3, 10, 13, "ad-1"));
        ticketLedgerRepository.save(TicketLedger.consumeForMatchingConnect(1L, 4, 13, 9, "match-1"));
        ticketLedgerRepository.save(TicketLedger.consumeForMatchingRecommendation(1L, 2, 9, 7, "recommend-1"));
        ticketLedgerRepository.save(TicketLedger.adjustByAdmin(1L, 20, 7, 27, "ADMIN:manual grant", "admin-grant-1"));
        ticketLedgerRepository.save(TicketLedger.adjustByAdmin(1L, -5, 27, 22, "ADMIN:manual revoke", "admin-revoke-1"));

        assertThat(ticketLedgerRepository.sumGrantedTickets()).isEqualTo(13L);
        assertThat(ticketLedgerRepository.sumConsumedTickets()).isEqualTo(6L);
    }
}
