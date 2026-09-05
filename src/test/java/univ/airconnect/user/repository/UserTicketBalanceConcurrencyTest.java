package univ.airconnect.user.repository;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real JPA transactions against a dedicated in-memory H2 database; no Boot context, application
 * configuration, environment files, external database, Redis, scheduler or production service.
 * The broad entity scan supplies mappings referenced by UserRepository's existing JPQL queries;
 * it is not a component scan. Tests have no enclosing test transaction and assert committed data.
 * H2 verifies persistence-context/SQL/locking regressions, not production-database isolation parity.
 */
@SpringJUnitConfig(UserTicketBalanceConcurrencyTest.IsolatedJpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class UserTicketBalanceConcurrencyTest {
    private static final int INITIAL_BALANCE = 10;
    private static final long WAIT_SECONDS = 8;

    @Autowired private UserRepository users;
    @Autowired private TicketLedgerRepository ledgers;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SqlCapture sqlCapture;

    private Long userId;

    @BeforeEach
    void setUp() {
        ledgers.deleteAllInBatch();
        users.deleteAllInBatch();
        userId = inTransaction(() -> users.saveAndFlush(User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("ticket-concurrency-" + UUID.randomUUID())
                .nickname("original")
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(INITIAL_BALANCE)
                .createdAt(LocalDateTime.now())
                .build()).getId());
        sqlCapture.clear();
    }

    @AfterEach
    void cleanUp() {
        ledgers.deleteAllInBatch();
        users.deleteAllInBatch();
        sqlCapture.clear();
    }

    @Test
    void lockRefreshesAlreadyManagedUserBeforeApplyingDelta() {
        assertStaleManagedAdjustment(5, -2, 13);
    }

    @Test
    void returningToOriginalSnapshotBalanceStillPersistsTheAdjustment() {
        // Merely copying 13 into an entity whose loaded snapshot is 10 is insufficient:
        // a following -3 returns to 10 and could otherwise be omitted by dirty checking.
        assertStaleManagedAdjustment(3, -3, 10);
        assertThat(ledgerRows()).extracting(TicketLedger::getBeforeAmount)
                .containsExactly(10, 13);
        assertThat(ledgerRows()).extracting(TicketLedger::getAfterAmount)
                .containsExactly(13, 10);
    }

    @Test
    void staleNicknameOnlyUpdateDoesNotWriteOrOverwriteTickets() {
        assertNonTicketUpdateDoesNotOverwriteTickets(user -> user.changeNickname("renamed"));
        assertThat(committedUser().getNickname()).isEqualTo("renamed");
        assertThat(committedUser().getLastNicknameChangedAt()).isNotNull();
    }

    @Test
    void staleActivityOnlyUpdateDoesNotWriteOrOverwriteTickets() {
        assertNonTicketUpdateDoesNotOverwriteTickets(User::markActive);
        assertThat(committedUser().getLastActiveAt()).isNotNull();
        assertThat(committedUser().getNickname()).isEqualTo("original");
    }

    @Test
    void ticketLockPreservesPendingNicknameChangeAndRefreshesBalance() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            inTransaction(() -> {
                User alreadyManaged = users.findById(userId).orElseThrow();
                awaitFuture(executor.submit(() -> adjustInTransaction(3, "concurrent-grant")));
                assertThat(alreadyManaged.getTickets()).isEqualTo(10);
                alreadyManaged.changeNickname("pending nickname");
                assertThat(alreadyManaged.getLastNicknameChangedAt()).isNotNull();

                sqlCapture.clear();
                User locked = users.findByIdForTicketUpdate(userId).orElseThrow();

                assertThat(locked).isSameAs(alreadyManaged);
                assertThat(locked.getNickname()).isEqualTo("pending nickname");
                assertThat(locked.getLastNicknameChangedAt()).isNotNull();
                assertThat(locked.getLastActiveAt()).isNotNull();
                assertThat(locked.getTickets()).isEqualTo(13);
                // The fragment must flush pending non-ticket state before refreshing it.
                assertThat(sqlCapture.userUpdates()).isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql).doesNotContain("tickets"));
                recordAdjustment(locked, -3, "pending-nickname-consume");
                users.flush();
                return null;
            });
        } finally {
            stopExecutor(executor);
        }

        User committed = committedUser();
        assertThat(committed.getNickname()).isEqualTo("pending nickname");
        assertThat(committed.getLastNicknameChangedAt()).isNotNull();
        assertThat(committed.getLastActiveAt()).isNotNull();
        assertLedgerChain(10, 3, -3);
    }

    @Test
    void simultaneousAdjustmentsSerializeWithAContinuousLedgerChain() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> inTransaction(() -> {
                User locked = users.findByIdForTicketUpdate(userId).orElseThrow();
                firstLocked.countDown();
                awaitLatch(releaseFirst, "release first ticket lock");
                recordAdjustment(locked, 7, "serialized-grant");
                return locked.getTickets();
            }));
            awaitLatch(firstLocked, "first transaction acquires ticket lock");
            Future<Integer> second = executor.submit(() -> inTransaction(() -> {
                secondAttempting.countDown();
                User locked = users.findByIdForTicketUpdate(userId).orElseThrow();
                recordAdjustment(locked, -4, "serialized-consume");
                return locked.getTickets();
            }));
            awaitLatch(secondAttempting, "second transaction attempts ticket lock");
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(awaitFuture(first)).isEqualTo(17);
            assertThat(awaitFuture(second)).isEqualTo(13);
        } finally {
            releaseFirst.countDown();
            stopExecutor(executor);
        }

        assertLedgerChain(13, 7, -4);
    }

    @Test
    void failedLedgerInsertRollsBackAlreadyFlushedBalanceAndLedger() {
        assertThatThrownBy(() -> inTransaction(() -> {
            User locked = users.findByIdForTicketUpdate(userId).orElseThrow();
            recordAdjustment(locked, 9, "duplicate-ledger-ref");
            users.flush();
            assertThat(sqlCapture.userUpdates()).anySatisfy(sql -> assertThat(sql).contains("tickets"));
            // Fail on a real database uniqueness constraint after both writes were flushed.
            ledgers.saveAndFlush(TicketLedger.adjustByAdmin(userId, 1, 19, 20,
                    "isolated test", "duplicate-ledger-ref"));
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRows()).isEmpty();
    }

    @Test
    void missingUserReturnsEmptyInsideTransaction() {
        assertThat(inTransaction(() -> users.findByIdForTicketUpdate(Long.MAX_VALUE))).isEmpty();
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRows()).isEmpty();
    }

    @Test
    void ticketLockRejectsCallsWithoutAnExistingTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThatThrownBy(() -> users.findByIdForTicketUpdate(userId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRows()).isEmpty();
    }

    private void assertStaleManagedAdjustment(int otherDelta, int ownDelta, int expectedBalance) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            inTransaction(() -> {
                User alreadyManaged = users.findById(userId).orElseThrow();
                awaitFuture(executor.submit(() -> adjustInTransaction(otherDelta, "other-transaction")));
                assertThat(alreadyManaged.getTickets()).isEqualTo(INITIAL_BALANCE);

                User locked = users.findByIdForTicketUpdate(userId).orElseThrow();
                assertThat(locked).isSameAs(alreadyManaged);
                assertThat(locked.getTickets()).isEqualTo(INITIAL_BALANCE + otherDelta);
                recordAdjustment(locked, ownDelta, "original-transaction");
                users.flush();
                return null;
            });
        } finally {
            stopExecutor(executor);
        }
        assertLedgerChain(expectedBalance, otherDelta, ownDelta);
    }

    private void assertNonTicketUpdateDoesNotOverwriteTickets(Consumer<User> mutation) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            inTransaction(() -> {
                User alreadyManaged = users.findById(userId).orElseThrow();
                awaitFuture(executor.submit(() -> adjustInTransaction(7, "other-ticket-update")));
                assertThat(alreadyManaged.getTickets()).isEqualTo(INITIAL_BALANCE);

                sqlCapture.clear();
                mutation.accept(alreadyManaged);
                users.flush();
                assertThat(sqlCapture.userUpdates()).isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql).doesNotContain("tickets"));
                return null;
            });
        } finally {
            stopExecutor(executor);
        }
        assertLedgerChain(17, 7);
    }

    private int adjustInTransaction(int delta, String refId) {
        return inTransaction(() -> {
            User locked = users.findByIdForTicketUpdate(userId).orElseThrow();
            recordAdjustment(locked, delta, refId);
            users.flush();
            return locked.getTickets();
        });
    }

    private void recordAdjustment(User user, int delta, String refId) {
        int before = user.getTickets();
        user.adjustTickets(delta);
        ledgers.saveAndFlush(TicketLedger.adjustByAdmin(user.getId(), delta, before,
                user.getTickets(), "isolated test", refId));
    }

    private User committedUser() {
        return inTransaction(() -> users.findById(userId).orElseThrow());
    }

    private List<TicketLedger> ledgerRows() {
        return inTransaction(() -> ledgers.findAll(Sort.by(Sort.Direction.ASC, "id")));
    }

    private void assertLedgerChain(int expectedBalance, int... deltas) {
        List<TicketLedger> rows = ledgerRows();
        assertThat(rows).hasSize(deltas.length);
        int balance = INITIAL_BALANCE;
        for (int index = 0; index < deltas.length; index++) {
            TicketLedger row = rows.get(index);
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getChangeAmount()).isEqualTo(deltas[index]);
            assertThat(row.getBeforeAmount()).isEqualTo(balance);
            balance += deltas[index];
            assertThat(row.getAfterAmount()).isEqualTo(balance);
        }
        assertThat(balance).isEqualTo(expectedBalance);
        assertThat(committedUser().getTickets()).isEqualTo(expectedBalance);
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
            throw new AssertionError("Interrupted while waiting for ticket transaction", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("Ticket transaction failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("Ticket transaction did not finish within the bounded wait", exception);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("all ticket concurrency workers terminated").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stopping ticket concurrency workers", exception);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final List<String> updates = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("update users ")) {
                updates.add(normalized);
            }
            return sql;
        }

        List<String> userUpdates() {
            return List.copyOf(updates);
        }

        void clear() {
            updates.clear();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, TicketLedgerRepository.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:user-ticket-balance-concurrency-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
                    "sa", "");
        }

        @Bean SqlCapture sqlCapture() {
            return new SqlCapture();
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                                                                          SqlCapture sqlCapture) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.session_factory.statement_inspector", sqlCapture));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
