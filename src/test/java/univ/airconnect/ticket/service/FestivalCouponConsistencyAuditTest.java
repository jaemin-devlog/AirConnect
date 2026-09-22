package univ.airconnect.ticket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.ticket.domain.entity.FestivalCoupon;
import univ.airconnect.ticket.repository.FestivalCouponRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Isolated H2 transaction checks; not a claim of MySQL isolation equivalence. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import(FestivalCouponService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FestivalCouponConsistencyAuditTest {
    @Autowired FestivalCouponService service;
    @Autowired FestivalCouponRepository coupons;
    @Autowired UserRepository users;
    @Autowired TicketLedgerRepository ledgers;
    @Autowired PlatformTransactionManager manager;

    Long user() {
        return users.saveAndFlush(User.createEmailUser(UUID.randomUUID() + "@test.invalid", "fixture-hash")).getId();
    }

    @AfterEach void cleanup() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            coupons.deleteAllInBatch();
            ledgers.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test void competingUsersReceiveOneGrantTotal() throws Exception {
        Long first = user(), second = user();
        int before = users.findById(first).orElseThrow().getTickets() + users.findById(second).orElseThrow().getTickets();
        coupons.saveAndFlush(FestivalCoupon.issue("123456"));
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> redeemAfter(gate, first));
            var b = pool.submit(() -> redeemAfter(gate, second));
            gate.countDown();
            assertThat((a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            assertThat(ledgers.count()).isEqualTo(1);
            int after = users.findById(first).orElseThrow().getTickets() + users.findById(second).orElseThrow().getTickets();
            assertThat(after - before).isEqualTo(5);
        } finally { pool.shutdownNow(); }
    }

    private boolean redeemAfter(CountDownLatch gate, Long id) throws InterruptedException {
        gate.await();
        try { service.redeem(id, "123456"); return true; }
        catch (BusinessException alreadyRedeemed) { return false; }
    }

    @Test void sameUserRetryReturnsErrorButDoesNotGrantTwice() {
        Long id = user();
        coupons.saveAndFlush(FestivalCoupon.issue("123456"));
        var first = service.redeem(id, "123456");
        int balance = users.findById(id).orElseThrow().getTickets();
        assertThatThrownBy(() -> service.redeem(id, "123456")).isInstanceOf(BusinessException.class);
        assertThat(users.findById(id).orElseThrow().getTickets()).isEqualTo(balance);
        assertThat(ledgers.count()).isEqualTo(1);
        assertThat(first).isNotNull();
    }

    @Test void transactionFailureRollsBackBalanceCouponAndLedger() {
        Long id = user();
        int before = users.findById(id).orElseThrow().getTickets();
        coupons.saveAndFlush(FestivalCoupon.issue("123456"));
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.redeem(id, "123456");
            throw new IllegalStateException("synthetic transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(id).orElseThrow().getTickets()).isEqualTo(before);
        assertThat(ledgers.count()).isZero();
        assertThat(coupons.findAll().get(0).isRedeemed()).isFalse();
    }
}
