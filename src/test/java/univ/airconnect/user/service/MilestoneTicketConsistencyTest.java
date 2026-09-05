package univ.airconnect.user.service;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
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
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.infrastructure.ProfileImageProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.domain.VerificationPurpose;
import univ.airconnect.verification.domain.entity.VerifiedSchoolEmail;
import univ.airconnect.verification.repository.VerifiedSchoolEmailRepository;
import univ.airconnect.verification.service.MailService;
import univ.airconnect.verification.service.VerificationService;
import univ.airconnect.verification.service.VerifiedEmailSession;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Public service methods, real Spring transaction proxies and repositories, dedicated in-memory H2.
 * No Boot context, configuration binding/files, external DB, Redis connection, mail or throttling.
 * Entity scanning only supplies mappings referenced by repository JPQL. All image bytes are synthetic
 * PNGs in @TempDir. Redis verification stubs represent a fresh valid code on each invocation; these
 * tests make no claims about Redis/token delivery rollback or production-database isolation parity.
 * There is no surrounding test transaction: assertions read committed data in a fresh transaction.
 */
@SpringJUnitConfig(MilestoneTicketConsistencyTest.IsolatedJpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class MilestoneTicketConsistencyTest {
    private static final int INITIAL_BALANCE = 10;
    private static final int EMAIL_REWARD = 3;
    private static final int IMAGE_REWARD = 2;
    private static final long WAIT_SECONDS = 8;
    private static final String VALID_CODE = "123456";
    private static final String ORIGINAL_IMAGE = "existing.png";
    private static final String REJECT_CONSTRAINT = "test_reject_milestone_history";

    @Autowired private VerificationService verificationService;
    @Autowired private UserProfileImageService imageService;
    @Autowired private UserRepository users;
    @Autowired private UserProfileRepository profiles;
    @Autowired private UserMilestoneRepository milestones;
    @Autowired private TicketLedgerRepository ledgers;
    @Autowired private VerifiedSchoolEmailRepository verifiedEmails;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ValueOperations<String, String> redisValues;
    @Autowired private MailService mail;
    @Autowired private AttemptThrottleService throttle;
    @Autowired private MilestoneRewardProperties rewards;
    @Autowired private ProfileImageProperties imageProperties;
    @Autowired private LedgerInsertCapture sqlCapture;

    @TempDir Path tempDir;
    private Long userId;
    private String email;
    private byte[] png;

    @BeforeEach
    void setUp() throws Exception {
        dropRejectConstraint();
        deleteFixtureRows();
        reset(redis, redisValues, mail, throttle);
        rewards.setEmailVerifiedTickets(EMAIL_REWARD);
        rewards.setProfileImageUploadedTickets(IMAGE_REWARD);
        imageProperties.setProfileImageDir(tempDir.toString());
        imageProperties.setProfileImageUrlBase("https://fixture.invalid/profile-images");
        imageProperties.setProfileImageMaxBytes(1024 * 1024L);
        imageProperties.setProfileImageMaxPixels(100L);
        imageProperties.setProfileImageAllowedFormats(List.of("png"));

        png = syntheticPng();
        Files.write(tempDir.resolve(ORIGINAL_IMAGE), png);
        userId = inTransaction(() -> {
            User user = users.saveAndFlush(User.builder()
                    .provider(SocialProvider.APPLE)
                    .socialId("milestone-fixture-" + UUID.randomUUID())
                    .nickname("milestone fixture")
                    .status(UserStatus.ACTIVE)
                    .onboardingStatus(OnboardingStatus.FULL)
                    .tickets(INITIAL_BALANCE)
                    .createdAt(LocalDateTime.now())
                    .build());
            profiles.saveAndFlush(UserProfile.builder().user(user)
                    .profileImagePath(ORIGINAL_IMAGE).updatedAt(LocalDateTime.now()).build());
            return user.getId();
        });
        email = "fixture-" + userId + "@office.hanseo.ac.kr";
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.get(anyString())).thenAnswer(invocation ->
                ("email_verification:" + email).equals(invocation.getArgument(0)) ? VALID_CODE : null);
        sqlCapture.clear();
    }

    @AfterEach
    void cleanUp() {
        dropRejectConstraint();
        deleteFixtureRows();
        sqlCapture.clear();
    }

    @Test
    void emailVerificationCommitsLinkedEmailBalanceMilestoneAndLedger() {
        VerifiedEmailSession session = verifyEmail();

        assertThat(session.email()).isEqualTo(email);
        assertThat(session.verificationToken()).isNotBlank();
        assertThat(committedUser().getVerifiedSchoolEmail()).isEqualTo(email);
        assertThat(verifiedEmails.findByEmailIgnoreCase(email)).isPresent();
        assertGrantedMilestone(MilestoneType.EMAIL_VERIFIED);
        assertSingleReward(MilestoneType.EMAIL_VERIFIED, EMAIL_REWARD);
    }

    @Test
    void imageUploadCommitsImageBalanceMilestoneAndLedger() throws Exception {
        String imageUrl = uploadImage();
        String storedImage = committedImagePath();

        assertThat(imageUrl).endsWith("/" + storedImage);
        assertThat(storedImage).isNotEqualTo(ORIGINAL_IMAGE);
        assertThat(Files.exists(tempDir.resolve(storedImage))).isTrue();
        assertThat(storedFiles()).containsExactly(storedImage);
        assertGrantedMilestone(MilestoneType.PROFILE_IMAGE_UPLOADED);
        assertSingleReward(MilestoneType.PROFILE_IMAGE_UPLOADED, IMAGE_REWARD);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void nonPositiveRewardsRecordBothMilestonesWithoutBalanceOrHistory(int configuredReward) {
        rewards.setEmailVerifiedTickets(configuredReward);
        rewards.setProfileImageUploadedTickets(configuredReward);

        verifyEmail();
        uploadImage();

        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRows()).isEmpty();
        assertGrantedMilestone(MilestoneType.EMAIL_VERIFIED);
        assertGrantedMilestone(MilestoneType.PROFILE_IMAGE_UPLOADED);
        assertThat(milestones.count()).isEqualTo(2);
    }

    @Test
    void repeatedPublicCallsGrantEachMilestoneOnlyOnce() throws Exception {
        verifyEmail();
        verifyEmail();
        uploadImage();
        uploadImage();

        assertTwoRewardLedgerChain();
        assertThat(milestones.count()).isEqualTo(2);
        assertThat(verifiedEmails.count()).isEqualTo(1);
        assertThat(storedFiles()).containsExactly(committedImagePath());
    }

    @Test
    void alreadyGrantedFixtureMilestonesAreNeitherRepaidNorBackfilled() {
        inTransaction(() -> {
            milestones.saveAndFlush(UserMilestone.create(userId, MilestoneType.EMAIL_VERIFIED));
            milestones.saveAndFlush(UserMilestone.create(userId, MilestoneType.PROFILE_IMAGE_UPLOADED));
            return null;
        });

        verifyEmail();
        uploadImage();

        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRows()).isEmpty();
        assertThat(milestones.count()).isEqualTo(2);
        assertThat(committedUser().getVerifiedSchoolEmail()).isEqualTo(email);
        assertThat(committedImagePath()).isNotEqualTo(ORIGINAL_IMAGE);
    }

    @Test
    void realLedgerCheckFailureRollsBackEmailLinkReservationBalanceAndMilestone() {
        rejectMilestoneLedgerInserts();

        Throwable failure = catchThrowable(this::verifyEmail);

        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
        assertThat(((SQLException) rootCause(failure)).getSQLState()).isEqualTo("23513");
        assertThat(sqlCapture.attempts()).isPositive();
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(committedUser().getVerifiedSchoolEmail()).isNull();
        assertThat(verifiedEmails.count()).isZero();
        assertThat(milestones.count()).isZero();
        assertThat(ledgerRows()).isEmpty();
        assertThat(committedImagePath()).isEqualTo(ORIGINAL_IMAGE);
    }

    @Test
    void realLedgerCheckFailureRollsBackImageProfileBalanceAndMilestone() throws Exception {
        rejectMilestoneLedgerInserts();

        Throwable failure = catchThrowable(this::uploadImage);

        // This public service translates storage errors; the only injected failure is the
        // real CHECK constraint, and the inspector confirms Hibernate emitted the INSERT.
        assertThat(failure).isInstanceOf(UserException.class);
        assertThat(((UserException) failure).getErrorCode()).isEqualTo(UserErrorCode.PROFILE_IMAGE_STORAGE_ERROR);
        assertThat(sqlCapture.attempts()).isPositive();
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE);
        assertThat(milestones.count()).isZero();
        assertThat(ledgerRows()).isEmpty();
        assertThat(committedImagePath()).isEqualTo(ORIGINAL_IMAGE);
        assertThat(storedFiles()).containsExactly(ORIGINAL_IMAGE);
        assertThat(Files.readAllBytes(tempDir.resolve(ORIGINAL_IMAGE))).isEqualTo(png);
    }

    @Test
    void concurrentPublicRewardsRefreshAlreadyManagedUsersAndKeepContinuousHistory() throws Exception {
        CountDownLatch bothUsersRead = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<VerifiedEmailSession> emailResult = executor.submit(() -> inTransaction(() -> {
                readUserThenAwaitOtherTransaction(bothUsersRead);
                return verifyEmail();
            }));
            Future<String> imageResult = executor.submit(() -> inTransaction(() -> {
                readUserThenAwaitOtherTransaction(bothUsersRead);
                return uploadImage();
            }));
            assertThat(awaitFuture(emailResult).email()).isEqualTo(email);
            assertThat(awaitFuture(imageResult)).endsWith("/" + committedImagePath());
        } finally {
            stopExecutor(executor);
        }

        assertTwoRewardLedgerChain();
        assertGrantedMilestone(MilestoneType.EMAIL_VERIFIED);
        assertGrantedMilestone(MilestoneType.PROFILE_IMAGE_UPLOADED);
        assertThat(milestones.count()).isEqualTo(2);
        assertThat(committedUser().getVerifiedSchoolEmail()).isEqualTo(email);
        assertThat(storedFiles()).containsExactly(committedImagePath());
    }

    @Test
    void concurrentEmailVerificationsGrantTheSameMilestoneOnlyOnce() {
        // Isolate reward concurrency from creation of the unique email reservation.
        inTransaction(() -> verifiedEmails.saveAndFlush(VerifiedSchoolEmail.reserve(email, userId)));
        CountDownLatch bothUsersRead = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<VerifiedEmailSession> first = executor.submit(() -> inTransaction(() -> {
                readUserThenAwaitOtherTransaction(bothUsersRead);
                return verifyEmail();
            }));
            Future<VerifiedEmailSession> second = executor.submit(() -> inTransaction(() -> {
                readUserThenAwaitOtherTransaction(bothUsersRead);
                return verifyEmail();
            }));
            assertThat(awaitFuture(first).email()).isEqualTo(email);
            assertThat(awaitFuture(second).email()).isEqualTo(email);
        } finally {
            stopExecutor(executor);
        }

        assertSingleReward(MilestoneType.EMAIL_VERIFIED, EMAIL_REWARD);
        assertGrantedMilestone(MilestoneType.EMAIL_VERIFIED);
        assertThat(milestones.count()).isEqualTo(1);
        assertThat(verifiedEmails.count()).isEqualTo(1);
        assertThat(verifiedEmails.findByEmailIgnoreCase(email).orElseThrow().getLinkedUserId()).isEqualTo(userId);
        assertThat(committedUser().getVerifiedSchoolEmail()).isEqualTo(email);
        assertThat(committedImagePath()).isEqualTo(ORIGINAL_IMAGE);
    }

    private VerifiedEmailSession verifyEmail() {
        return verificationService.verifyCode(userId, email, VALID_CODE, VerificationPurpose.SIGN_UP,
                "isolated-fixture");
    }

    private String uploadImage() {
        return imageService.saveProfileImage(userId,
                new MockMultipartFile("file", "synthetic.png", "image/png", png));
    }

    private void readUserThenAwaitOtherTransaction(CountDownLatch bothUsersRead) {
        assertThat(users.findById(userId).orElseThrow().getTickets()).isEqualTo(INITIAL_BALANCE);
        bothUsersRead.countDown();
        try {
            assertThat(bothUsersRead.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("both transactions read the initial User before either reward starts").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while arranging concurrent milestone rewards", exception);
        }
    }

    private void assertGrantedMilestone(MilestoneType type) {
        UserMilestone milestone = inTransaction(() -> milestones.findByUserIdAndMilestoneType(userId, type)
                .orElseThrow());
        assertThat(milestone.getGranted()).isTrue();
        assertThat(milestone.getGrantedAt()).isNotNull();
    }

    private void assertSingleReward(MilestoneType type, int amount) {
        List<TicketLedger> rows = ledgerRows();
        assertThat(rows).hasSize(1);
        assertRewardReference(rows.get(0), type, amount);
        assertThat(rows.get(0).getBeforeAmount()).isEqualTo(INITIAL_BALANCE);
        assertThat(rows.get(0).getAfterAmount()).isEqualTo(INITIAL_BALANCE + amount);
        assertThat(committedUser().getTickets()).isEqualTo(INITIAL_BALANCE + amount);
    }

    private void assertTwoRewardLedgerChain() {
        List<TicketLedger> rows = ledgerRows();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(TicketLedger::getChangeAmount)
                .containsExactlyInAnyOrder(EMAIL_REWARD, IMAGE_REWARD);
        int balance = INITIAL_BALANCE;
        for (TicketLedger row : rows) {
            MilestoneType type = MilestoneType.valueOf(row.getReason());
            assertRewardReference(row, type,
                    type == MilestoneType.EMAIL_VERIFIED ? EMAIL_REWARD : IMAGE_REWARD);
            assertThat(row.getBeforeAmount()).isEqualTo(balance);
            balance += row.getChangeAmount();
            assertThat(row.getAfterAmount()).isEqualTo(balance);
        }
        assertThat(rows).extracting(TicketLedger::getRefId).containsExactlyInAnyOrder(
                "milestone:" + userId + ":EMAIL_VERIFIED",
                "milestone:" + userId + ":PROFILE_IMAGE_UPLOADED");
        assertThat(balance).isEqualTo(INITIAL_BALANCE + EMAIL_REWARD + IMAGE_REWARD);
        assertThat(committedUser().getTickets()).isEqualTo(balance);
    }

    private void assertRewardReference(TicketLedger row, MilestoneType type, int amount) {
        assertThat(row.getUserId()).isEqualTo(userId);
        assertThat(row.getRefType()).isEqualTo(LedgerRefType.MILESTONE_REWARD);
        assertThat(row.getRefId()).isEqualTo("milestone:" + userId + ":" + type.name());
        assertThat(row.getReason()).isEqualTo(type.name());
        assertThat(row.getChangeAmount()).isEqualTo(amount);
    }

    private User committedUser() {
        return inTransaction(() -> users.findById(userId).orElseThrow());
    }

    private String committedImagePath() {
        return inTransaction(() -> profiles.findByUserId(userId).orElseThrow().getProfileImagePath());
    }

    private List<TicketLedger> ledgerRows() {
        return inTransaction(() -> ledgers.findAll(Sort.by(Sort.Direction.ASC, "id")));
    }

    private List<String> storedFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private void rejectMilestoneLedgerInserts() {
        jdbc.execute("ALTER TABLE ticket_ledger ADD CONSTRAINT " + REJECT_CONSTRAINT
                + " CHECK (ref_type <> 'MILESTONE_REWARD')");
    }

    private void dropRejectConstraint() {
        jdbc.execute("ALTER TABLE ticket_ledger DROP CONSTRAINT IF EXISTS " + REJECT_CONSTRAINT);
    }

    private void deleteFixtureRows() {
        inTransaction(() -> {
            ledgers.deleteAllInBatch();
            milestones.deleteAllInBatch();
            verifiedEmails.deleteAllInBatch();
            profiles.deleteAllInBatch();
            users.deleteAllInBatch();
            return null;
        });
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(12);
        return transaction.execute(status -> action.get());
    }

    private static byte[] syntheticPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff346789);
        image.setRGB(1, 1, 0xff987654);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, "png", bytes)).isTrue();
            return bytes.toByteArray();
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> T awaitFuture(Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for milestone transaction", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("Milestone transaction failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("Milestone transaction exceeded bounded wait", exception);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("all milestone concurrency workers terminated").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted stopping milestone concurrency workers", exception);
        }
    }

    static class LedgerInsertCapture implements StatementInspector {
        private final AtomicInteger inserts = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            if (sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("insert into ticket_ledger ")) {
                inserts.incrementAndGet();
            }
            return sql;
        }

        int attempts() { return inserts.get(); }
        void clear() { inserts.set(0); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {
            UserRepository.class, TicketLedgerRepository.class, VerifiedSchoolEmailRepository.class
    })
    @Import({VerificationService.class, UserProfileImageService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:milestone-ticket-consistency-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
                    "sa", "");
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean LedgerInsertCapture ledgerInsertCapture() { return new LedgerInsertCapture(); }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                                                                          LedgerInsertCapture sqlCapture) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                    "hibernate.session_factory.statement_inspector", sqlCapture));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean StringRedisTemplate redisTemplate() { return mock(StringRedisTemplate.class); }
        @Bean @SuppressWarnings("unchecked") ValueOperations<String, String> redisValues() {
            return mock(ValueOperations.class);
        }
        @Bean MailService mailService() { return mock(MailService.class); }
        @Bean AttemptThrottleService attemptThrottleService() { return mock(AttemptThrottleService.class); }
        @Bean MilestoneRewardProperties milestoneRewardProperties() { return new MilestoneRewardProperties(); }
        @Bean ProfileImageProperties profileImageProperties() { return new ProfileImageProperties(); }
    }
}
