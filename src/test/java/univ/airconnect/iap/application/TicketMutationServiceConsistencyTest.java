package univ.airconnect.iap.application;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.ticket.repository.AdTicketLedgerRepository;
import univ.airconnect.ticket.service.AdTicketGrantService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real service proxies, JPA repositories and committed transactions in a dedicated H2 database.
 * Only the three ticket mutation services are imported: no Boot context, application configuration,
 * store verifier, ad-session validation, environment files, external database or Redis is loaded.
 * Orders and ad-session references are synthetic inputs at the ticket-mutation service boundary.
 * The broad entity scan supplies mappings required by existing repository JPQL, not components.
 * H2 exercises persistence-context and locking regressions, not production-database isolation parity.
 */
@SpringJUnitConfig(TicketMutationServiceConsistencyTest.IsolatedJpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class TicketMutationServiceConsistencyTest {
    private static final int INITIAL_BALANCE = 10;
    private static final long SESSION_ID = 41001L;
    private static final long WAIT_SECONDS = 8;
    private static final String FAILURE_CONSTRAINT = "ck_ticket_mutation_fixture_failure";

    @Autowired private TicketGrantService purchaseGrants;
    @Autowired private IapRefundService refunds;
    @Autowired private AdTicketGrantService adGrants;
    @Autowired private UserRepository users;
    @Autowired private IapOrderRepository orders;
    @Autowired private TicketLedgerRepository ticketHistory;
    @Autowired private AdTicketLedgerRepository adHistory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
        userId = inTransaction(() -> users.saveAndFlush(User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("ticket-service-fixture-" + UUID.randomUUID())
                .nickname("original")
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(INITIAL_BALANCE)
                .createdAt(LocalDateTime.now())
                .build()).getId());
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("ALTER TABLE ticket_ledger DROP CONSTRAINT IF EXISTS " + FAILURE_CONSTRAINT);
        ticketHistory.deleteAllInBatch();
        orders.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @ParameterizedTest(name = "{0}: latest balance and pending nickname survive")
    @EnumSource(Mutation.class)
    void mutationRefreshesStaleUserAndPreservesPendingNonTicketChanges(Mutation mutation) {
        IapOrder order = fixtureOrderFor(mutation);
        String pendingNickname = "pending " + mutation.name();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Amounts result;
        try {
            result = inTransaction(() -> {
                User stale = users.findById(userId).orElseThrow();
                stale.changeNickname(pendingNickname);
                assertThat(awaitFuture(executor.submit(this::creditInAnotherTransaction))).isEqualTo(13);
                assertThat(stale.getTickets()).isEqualTo(INITIAL_BALANCE);

                Amounts amounts = mutate(mutation, order);

                assertThat(stale.getNickname()).isEqualTo(pendingNickname);
                assertThat(stale.getLastNicknameChangedAt()).isNotNull();
                assertThat(stale.getTickets()).isEqualTo(13 + mutation.delta);
                return amounts;
            });
        } finally {
            stopExecutor(executor);
        }

        assertThat(result.before()).isEqualTo(13);
        assertThat(result.after()).isEqualTo(13 + mutation.delta);
        assertThat(result.reference()).startsWith("TICKET_LEDGER_");
        User committed = committedUser();
        assertThat(committed.getNickname()).isEqualTo(pendingNickname);
        assertThat(committed.getLastNicknameChangedAt()).isNotNull();
        assertThat(committed.getLastActiveAt()).isNotNull();
        // REFUND returns to the original loaded snapshot of 10, detecting dirty-checking loss.
        assertHistoryChain(INITIAL_BALANCE, 3, mutation.delta);
        TicketLedger ownChange = historyRows().get(1);
        assertThat(ownChange.getRefType()).isEqualTo(mutation.refType);
        assertThat(ownChange.getRefId()).isEqualTo(referenceId(mutation, order));
        assertThat(ownChange.ledgerExternalId()).isEqualTo(result.reference());
        if (mutation == Mutation.REFUND) {
            assertThat(committedOrder(order).getStatus()).isEqualTo(IapOrderStatus.REFUNDED);
        }
    }

    @ParameterizedTest(name = "{0}: real history constraint failure rolls back")
    @EnumSource(Mutation.class)
    void databaseHistoryFailureRollsBackBalanceAndOtherPendingWrites(Mutation mutation) {
        IapOrder order = fixtureOrderFor(mutation);
        // A real DB CHECK rejects only this operation's history row; no repository is mocked.
        jdbc.execute("ALTER TABLE ticket_ledger ADD CONSTRAINT " + FAILURE_CONSTRAINT
                + " CHECK (ref_type <> '" + mutation.refType.name() + "')");

        assertThatThrownBy(() -> inTransaction(() -> {
            User user = users.findById(userId).orElseThrow();
            user.changeNickname("must roll back");
            users.flush();
            return mutate(mutation, order);
        })).isInstanceOf(RuntimeException.class);
        // Existing duplicate-recovery catches can change the propagated exception type; the
        // invariant is that the failed real insert cannot leave a committed partial operation.
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(committedUser().getNickname()).isEqualTo("original");
        assertThat(historyRows()).isEmpty();
        assertThat(adHistory.count()).isZero();
        if (order != null) {
            IapOrder committed = committedOrder(order);
            assertThat(committed.getStatus()).isEqualTo(order.getStatus());
            assertThat(committed.getGrantedTickets()).isEqualTo(order.getGrantedTickets());
            assertThat(committed.getBeforeTickets()).isEqualTo(order.getBeforeTickets());
            assertThat(committed.getAfterTickets()).isEqualTo(order.getAfterTickets());
        }
    }

    @Test
    void concurrentPurchaseAndAdCreditSerializeIntoContinuousHistory() {
        IapOrder order = createOrder(false, 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch purchaseLocked = new CountDownLatch(1);
        CountDownLatch adAttempting = new CountDownLatch(1);
        CountDownLatch releasePurchase = new CountDownLatch(1);
        try {
            Future<TicketGrantService.TicketGrantResult> purchase = executor.submit(() -> inTransaction(() -> {
                users.findByIdForTicketUpdate(userId).orElseThrow();
                purchaseLocked.countDown();
                awaitLatch(releasePurchase, "release purchase transaction");
                return purchaseGrants.grantTickets(order, Mutation.PURCHASE.delta);
            }));
            awaitLatch(purchaseLocked, "purchase acquires the user lock");
            Future<AdTicketGrantService.GrantResult> ad = executor.submit(() -> inTransaction(() -> {
                User stale = users.findById(userId).orElseThrow();
                assertThat(stale.getTickets()).isEqualTo(INITIAL_BALANCE);
                adAttempting.countDown();
                return adGrants.grantFromAdReward(userId, Mutation.AD.delta, SESSION_ID);
            }));
            awaitLatch(adAttempting, "ad transaction reads the old balance and attempts its grant");
            assertThatThrownBy(() -> ad.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releasePurchase.countDown();

            TicketGrantService.TicketGrantResult purchaseResult = awaitFuture(purchase);
            AdTicketGrantService.GrantResult adResult = awaitFuture(ad);
            assertThat(purchaseResult.beforeTickets()).isEqualTo(10);
            assertThat(purchaseResult.afterTickets()).isEqualTo(15);
            assertThat(adResult.granted()).isTrue();
            assertThat(adResult.beforeTickets()).isEqualTo(15);
            assertThat(adResult.afterTickets()).isEqualTo(17);
            assertThat(adResult.ledgerExternalId()).isNotEqualTo(purchaseResult.ledgerExternalId());
        } finally {
            releasePurchase.countDown();
            stopExecutor(executor);
        }

        assertHistoryChain(INITIAL_BALANCE, 5, 2);
        assertThat(historyRows()).extracting(TicketLedger::getRefType)
                .containsExactly(LedgerRefType.IAP_ORDER, LedgerRefType.AD_REWARD_SESSION);
        assertThat(historyRows()).extracting(TicketLedger::getRefId)
                .containsExactly(String.valueOf(order.getId()), String.valueOf(SESSION_ID));
    }

    @Test
    void refundMayMakeBalanceNegativeAndRepeatedRefundDoesNotDeductAgain() {
        IapOrder order = createOrder(true, 70);
        inTransaction(() -> {
            users.findByIdForTicketUpdate(userId).orElseThrow().adjustTickets(-7);
            return null;
        });

        IapRefundService.RefundResult first = refunds.refundAppleTransaction(
                order.getTransactionId(), "synthetic-refund-fixture");
        IapRefundService.RefundResult second = refunds.refundAppleTransaction(
                order.getTransactionId(), "synthetic-refund-fixture");

        assertThat(first.handled()).isTrue();
        assertThat(first.refunded()).isTrue();
        assertThat(first.revoked()).isFalse();
        assertThat(first.beforeTickets()).isEqualTo(3);
        assertThat(first.afterTickets()).isEqualTo(-67);
        assertThat(second.handled()).isTrue();
        assertThat(second.refunded()).isFalse();
        assertThat(second.revoked()).isFalse();
        assertThat(second.reference()).isEqualTo("already_refunded");
        assertThat(committedOrder(order).getStatus()).isEqualTo(IapOrderStatus.REFUNDED);
        assertHistoryChain(3, -70);
        TicketLedger change = historyRows().get(0);
        assertThat(change.getRefType()).isEqualTo(LedgerRefType.IAP_REFUND);
        assertThat(change.getRefId()).isEqualTo(String.valueOf(order.getId()));
        assertThat(change.ledgerExternalId()).isEqualTo(first.reference());
    }

    @Test
    void repeatedPurchaseReferenceReturnsTheOriginalChangeWithoutGrantingAgain() {
        IapOrder order = createOrder(false, 0);

        TicketGrantService.TicketGrantResult first = purchaseGrants.grantTickets(order, 5);
        TicketGrantService.TicketGrantResult second = purchaseGrants.grantTickets(order, 5);

        assertThat(second).isEqualTo(first);
        assertThat(first.beforeTickets()).isEqualTo(10);
        assertThat(first.afterTickets()).isEqualTo(15);
        assertHistoryChain(INITIAL_BALANCE, 5);
        assertThat(historyRows().get(0).getRefType()).isEqualTo(LedgerRefType.IAP_ORDER);
        assertThat(historyRows().get(0).getRefId()).isEqualTo(String.valueOf(order.getId()));
        // Order completion belongs to the existing caller, not TicketGrantService itself.
        assertThat(committedOrder(order).getStatus()).isEqualTo(IapOrderStatus.VERIFIED);
    }

    @Test
    void repeatedAdSessionReferenceReturnsTheOriginalChangeWithoutGrantingAgain() {
        AdTicketGrantService.GrantResult first = adGrants.grantFromAdReward(userId, 2, SESSION_ID);
        AdTicketGrantService.GrantResult second = adGrants.grantFromAdReward(userId, 2, SESSION_ID);

        assertThat(first.granted()).isTrue();
        assertThat(second.granted()).isFalse();
        assertThat(first.beforeTickets()).isEqualTo(10);
        assertThat(first.afterTickets()).isEqualTo(12);
        assertThat(second.beforeTickets()).isEqualTo(first.beforeTickets());
        assertThat(second.afterTickets()).isEqualTo(first.afterTickets());
        assertThat(second.ledgerExternalId()).isEqualTo(first.ledgerExternalId());
        assertHistoryChain(INITIAL_BALANCE, 2);
        TicketLedger change = adHistory.findByRefTypeAndRefId(
                LedgerRefType.AD_REWARD_SESSION, String.valueOf(SESSION_ID)).orElseThrow();
        assertThat(change.ledgerExternalId()).isEqualTo(first.ledgerExternalId());
        assertThat(adHistory.count()).isEqualTo(1);
    }

    private Amounts mutate(Mutation mutation, IapOrder order) {
        return switch (mutation) {
            case PURCHASE -> {
                var result = purchaseGrants.grantTickets(order, mutation.delta);
                yield new Amounts(result.beforeTickets(), result.afterTickets(), result.ledgerExternalId());
            }
            case AD -> {
                var result = adGrants.grantFromAdReward(userId, mutation.delta, SESSION_ID);
                assertThat(result.granted()).isTrue();
                yield new Amounts(result.beforeTickets(), result.afterTickets(), result.ledgerExternalId());
            }
            case REFUND -> {
                var result = refunds.refundGrantedOrder(order, "synthetic-refund-fixture");
                assertThat(result.refunded()).isTrue();
                yield new Amounts(result.beforeTickets(), result.afterTickets(), result.reference());
            }
        };
    }

    private IapOrder fixtureOrderFor(Mutation mutation) {
        return mutation == Mutation.AD ? null : createOrder(mutation == Mutation.REFUND, 3);
    }

    private IapOrder createOrder(boolean granted, int amount) {
        return inTransaction(() -> {
            IapOrder order = IapOrder.createPending(userId, IapStore.APPLE, "synthetic-ticket-product",
                    "synthetic-transaction-" + UUID.randomUUID(), null, null, null,
                    "synthetic-account-fixture", IapEnvironment.SANDBOX,
                    "synthetic-verification-fixture", "{}");
            order.markVerified();
            if (granted) {
                order.markGranted(amount, 0, amount);
            }
            return orders.saveAndFlush(order);
        });
    }

    private String referenceId(Mutation mutation, IapOrder order) {
        return String.valueOf(mutation == Mutation.AD ? SESSION_ID : order.getId());
    }

    private int creditInAnotherTransaction() {
        return inTransaction(() -> {
            User user = users.findByIdForTicketUpdate(userId).orElseThrow();
            int before = user.getTickets();
            user.addTickets(3);
            ticketHistory.saveAndFlush(TicketLedger.adjustByAdmin(userId, 3, before, user.getTickets(),
                    "isolated fixture credit", "concurrent-credit-" + UUID.randomUUID()));
            return user.getTickets();
        });
    }

    private User committedUser() {
        return inTransaction(() -> users.findById(userId).orElseThrow());
    }

    private IapOrder committedOrder(IapOrder order) {
        return inTransaction(() -> orders.findById(order.getId()).orElseThrow());
    }

    private List<TicketLedger> historyRows() {
        return inTransaction(() -> ticketHistory.findAll(Sort.by(Sort.Direction.ASC, "id")));
    }

    private void assertHistoryChain(int initialBalance, int... deltas) {
        List<TicketLedger> rows = historyRows();
        assertThat(rows).hasSize(deltas.length);
        int balance = initialBalance;
        for (int index = 0; index < deltas.length; index++) {
            TicketLedger row = rows.get(index);
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getChangeAmount()).isEqualTo(deltas[index]);
            assertThat(row.getBeforeAmount()).isEqualTo(balance);
            balance += deltas[index];
            assertThat(row.getAfterAmount()).isEqualTo(balance);
        }
        assertThat(committedUser().getTickets()).isEqualTo(balance);
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(12);
        return transaction.execute(status -> action.get());
    }

    private static void awaitLatch(CountDownLatch latch, String phase) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as(phase).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to " + phase, exception);
        }
    }

    private static <T> T awaitFuture(Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for ticket mutation", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("Ticket mutation worker failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("Ticket mutation exceeded the bounded wait", exception);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("all ticket mutation workers terminated").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stopping ticket mutation workers", exception);
        }
    }

    private record Amounts(int before, int after, String reference) { }

    enum Mutation {
        PURCHASE(LedgerRefType.IAP_ORDER, 5),
        AD(LedgerRefType.AD_REWARD_SESSION, 2),
        REFUND(LedgerRefType.IAP_REFUND, -3);

        final LedgerRefType refType;
        final int delta;

        Mutation(LedgerRefType refType, int delta) {
            this.refType = refType;
            this.delta = delta;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, IapOrderRepository.class,
            TicketLedgerRepository.class, AdTicketLedgerRepository.class})
    @Import({TicketGrantService.class, IapRefundService.class, AdTicketGrantService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:ticket-mutation-services-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
                    "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
