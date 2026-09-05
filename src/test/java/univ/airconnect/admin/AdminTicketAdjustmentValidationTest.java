package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.RestAccessDeniedHandler;
import univ.airconnect.global.security.RestAuthenticationEntryPoint;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin input/arithmetic validation with the real service, User/TicketLedger repositories,
 * explicit service transactions and an in-memory H2 database. No Boot application, environment
 * files, production resources, scheduler or notification transport are loaded. Fixtures are
 * committed before each service call so a missing rollback cannot be hidden by a test-level
 * transaction. Notification and audit behavior is checked at their mocked call boundaries;
 * these tests do not claim to exercise either external delivery or the audit service's own TX.
 */
@SpringJUnitConfig(AdminTicketAdjustmentValidationTest.IsolatedJpaConfig.class)
class AdminTicketAdjustmentValidationTest {
    private static final String ENDPOINT = "/api/v1/admin/tickets/adjustments";
    private static final String FAILURE_REASON = "force-ledger-failure";

    @Autowired AdminTicketAdjustmentService adjustmentService;
    @Autowired AdminTicketAdjustmentRepository operations;
    @Autowired ObjectMapper objectMapper;
    @Autowired LocalValidatorFactoryBean validator;
    @Autowired JpaTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean TicketLedgerRepository ledger;

    @MockitoBean AdminNoticeRepository notices;
    @MockitoBean UserReportRepository reports;
    @MockitoBean MatchingConnectionRepository matchingConnections;
    @MockitoBean IapOrderRepository orders;
    @MockitoBean AnalyticsEventRepository analytics;
    @MockitoBean ChatRoomRepository rooms;
    @MockitoBean ChatRoomMemberRepository members;
    @MockitoBean ChatMessageRepository messages;
    @MockitoBean UserService userService;
    @MockitoBean NotificationService notifications;
    @MockitoBean StatisticsService statistics;
    @MockitoBean AdminAuditLogService audits;

    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private Long adminId;
    private Long userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        transaction = new TransactionTemplate(transactionManager);
        jdbc = new JdbcTemplate(dataSource);
        transaction.executeWithoutResult(tx -> {
            adminId = users.save(user("operator", UserRole.ADMIN, 10)).getId();
            userId = users.save(user("recipient", UserRole.USER, 10)).getId();
            ledger.save(TicketLedger.adjustByAdmin(userId, 2, 8, 10,
                    "ADMIN:existing-fixture", "validation-existing-" + UUID.randomUUID()));
        });
        clearInvocations(users, ledger, notifications, audits);

        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("ticket-validation-fixture-anonymous"), translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(new AdminTicketAdjustmentController(adjustmentService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .addFilters(security)
                .build();
        var principal = new CustomUserPrincipal(adminId, UserRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        transaction.executeWithoutResult(tx -> {
            operations.deleteAllInBatch();
            ledger.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "가"})
    void trimmedFiftyFourCharacterReasonPassesHttpValidationAndFitsTheRealLedger(String character) throws Exception {
        String reason = character.repeat(54);
        var request = request(userId, 5, " \t" + reason + "\n ");
        assertThat(request.reason()).isEqualTo(reason);
        assertThat(validator.validate(request)).isEmpty();

        // Submit the untrimmed JSON too, to verify Jackson uses the compact constructor.
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operationId", request.operationId(),
                                "userId", userId, "amount", 5, "reason", " \t" + reason + "\n "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.afterTickets").value(15));

        assertCommittedAdjustment(10, 5, 15, reason);
        assertThat(savedAdjustment().getReason()).hasSize(60);
        assertWriteOrderAndAnnouncement(reason);
    }

    enum InvalidReason { NULL, EMPTY, WHITESPACE, FIFTY_FIVE_CHARACTERS }

    @ParameterizedTest
    @EnumSource(InvalidReason.class)
    void invalidReasonIsRejectedByBothBeanValidationAndServiceWithoutWrites(InvalidReason invalid) throws Exception {
        String reason = switch (invalid) {
            case NULL -> null;
            case EMPTY -> "";
            case WHITESPACE -> " \t\n ";
            case FIFTY_FIVE_CHARACTERS -> "  " + "가".repeat(55) + "  ";
        };
        var request = request(userId, 1, reason);
        var before = databaseState();
        assertThat(validator.validate(request)).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("reason"));
        assertInvalid(request);
        expectHttpBadRequest(request);
        assertThat(databaseState()).isEqualTo(before);
        assertThat(operations.count()).isZero();
        verify(users, never()).findByIdForTicketUpdate(any());
        verify(ledger, never()).save(any());
        verifyNoInteractions(notifications, audits);
    }

    enum InvalidInput { NULL_REQUEST, NULL_USER_ID, ZERO_USER_ID, NEGATIVE_USER_ID, ZERO_AMOUNT, MINIMUM_AMOUNT }

    @ParameterizedTest
    @EnumSource(InvalidInput.class)
    void invalidRequestIsRejectedBeforeTakingTheUserLock(InvalidInput invalid) throws Exception {
        AdminRequests.TicketAdjustmentRequest request = switch (invalid) {
            case NULL_REQUEST -> null;
            case NULL_USER_ID -> request(null, 1, "fixture reason");
            case ZERO_USER_ID -> request(0L, 1, "fixture reason");
            case NEGATIVE_USER_ID -> request(-1L, 1, "fixture reason");
            case ZERO_AMOUNT -> request(userId, 0, "fixture reason");
            case MINIMUM_AMOUNT -> request(userId, Integer.MIN_VALUE, "fixture reason");
        };
        var before = databaseState();
        assertInvalid(request);
        expectHttpBadRequest(request);
        assertThat(databaseState()).isEqualTo(before);
        assertThat(operations.count()).isZero();
        verify(users, never()).findByIdForTicketUpdate(any());
        verify(ledger, never()).save(any());
        verifyNoInteractions(notifications, audits);
    }

    enum InvalidArithmetic {
        OVERFLOW_AT_MAXIMUM(Integer.MAX_VALUE, 1),
        OVERFLOW_FROM_ORDINARY_BALANCE(10, Integer.MAX_VALUE),
        OVERDRAFT(10, -11),
        DEBIT_FROM_EXISTING_NEGATIVE_BALANCE(-5, -1),
        UNDERFLOW_AT_MINIMUM(Integer.MIN_VALUE, -1);

        final int before;
        final int amount;
        InvalidArithmetic(int before, int amount) { this.before = before; this.amount = amount; }
    }

    @ParameterizedTest
    @EnumSource(InvalidArithmetic.class)
    void overflowAndOverdraftLeaveTheOriginalBalanceAndLedgerUntouched(InvalidArithmetic invalid) throws Exception {
        setBalance(invalid.before);
        var request = request(userId, invalid.amount, "arithmetic fixture");
        var before = databaseState();
        var result = adjustmentService.adjust(adminId, request);
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectionCode()).isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        assertThat(result.beforeTickets()).isEqualTo(invalid.before);
        assertThat(result.afterTickets()).isEqualTo(invalid.before);
        assertThat(result.ledgerId()).isNull();
        assertThat(result.completedAt()).isNotNull();
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.operationId").value(request.operationId()));
        assertThat(databaseState()).isEqualTo(before);
        assertThat(operations.count()).isEqualTo(1);
        verify(ledger, never()).save(any());
        verifyNoInteractions(notifications, audits);
    }

    enum ValidArithmetic {
        EXACT_SPEND(10, -10, 0),
        PARTIAL_SPEND(10, -3, 7),
        NEGATIVE_BALANCE_REMAINS_NEGATIVE(-5, 2, -3),
        NEGATIVE_BALANCE_REACHES_ZERO(-5, 5, 0),
        NEGATIVE_BALANCE_BECOMES_POSITIVE(-5, 8, 3),
        REACHES_MAXIMUM_WITHOUT_OVERFLOW(Integer.MAX_VALUE - 1, 1, Integer.MAX_VALUE),
        MINIMUM_BALANCE_CAN_RECEIVE_COMPENSATION(Integer.MIN_VALUE, 1, Integer.MIN_VALUE + 1);

        final int before;
        final int amount;
        final int after;
        ValidArithmetic(int before, int amount, int after) {
            this.before = before; this.amount = amount; this.after = after;
        }
    }

    @ParameterizedTest
    @EnumSource(ValidArithmetic.class)
    void validAdjustmentPreservesExistingDebitAndNegativeBalanceCompensationPolicy(ValidArithmetic valid) {
        setBalance(valid.before);
        var result = adjustmentService.adjust(adminId,
                request(userId, valid.amount, "  valid fixture  "));
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.afterTickets()).isEqualTo(valid.after);
        assertCommittedAdjustment(valid.before, valid.amount, valid.after, "valid fixture");
        assertWriteOrderAndAnnouncement("valid fixture");
    }

    @Test
    void unknownPositiveUserIdReturnsDurableRejectionWithoutDomainWrites() {
        var before = databaseState();
        var result = adjustmentService.adjust(adminId, request(Long.MAX_VALUE, 1, "unknown user fixture"));
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.rejectionCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(result.beforeTickets()).isNull();
        assertThat(result.afterTickets()).isNull();
        assertThat(result.ledgerId()).isNull();
        assertThat(databaseState()).isEqualTo(before);
        assertThat(operations.count()).isEqualTo(1);
        verify(users).findByIdForTicketUpdate(Long.MAX_VALUE);
        verify(ledger, never()).save(any());
        verifyNoInteractions(notifications, audits);
    }

    @Test
    void actualLedgerInsertFailureRollsBackTheAlreadyChangedUserBalance() {
        // The only database is the explicit test H2 bean below. This constraint rejects a real
        // INSERT, not a mock repository call, after adjust has mutated its managed User.
        jdbc.execute("ALTER TABLE ticket_ledger ADD CONSTRAINT ck_ticket_validation_fixture "
                + "CHECK (reason <> 'ADMIN:force-ledger-failure')");
        try {
            var before = databaseState();
            assertThatThrownBy(() -> adjustmentService.adjust(adminId,
                    request(userId, 5, FAILURE_REASON)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(databaseState()).isEqualTo(before);
            assertThat(operations.count()).isZero();
            verify(users).findByIdForTicketUpdate(userId);
            verify(ledger).save(any(TicketLedger.class));
            verifyNoInteractions(notifications, audits);
        } finally {
            jdbc.execute("ALTER TABLE ticket_ledger DROP CONSTRAINT ck_ticket_validation_fixture");
        }
    }

    private void assertInvalid(AdminRequests.TicketAdjustmentRequest request) {
        assertThatThrownBy(() -> adjustmentService.adjust(adminId, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private void expectHttpBadRequest(AdminRequests.TicketAdjustmentRequest request) throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private void assertCommittedAdjustment(int before, int amount, int after, String reason) {
        assertThat(operations.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tickets FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(after);
        assertThat(jdbc.queryForObject("SELECT tickets FROM users WHERE id = ?", Integer.class, adminId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ledger", Long.class)).isEqualTo(2L);
        TicketLedger entry = savedAdjustment();
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getBeforeAmount()).isEqualTo(before);
        assertThat(entry.getChangeAmount()).isEqualTo(amount);
        assertThat(entry.getAfterAmount()).isEqualTo(after);
        assertThat(entry.getReason()).isEqualTo("ADMIN:" + reason);
        assertThat(entry.getRefType()).isEqualTo(LedgerRefType.ADMIN_ADJUSTMENT);
        assertThat(entry.getRefId()).startsWith("admin-adjustment:");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    private TicketLedger savedAdjustment() {
        return transaction.execute(tx -> ledger.findAll().stream()
                .filter(entry -> entry.getRefId().startsWith("admin-adjustment:"))
                .findFirst().orElseThrow());
    }

    private void assertWriteOrderAndAnnouncement(String reason) {
        var notification = ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        var order = inOrder(users, ledger, notifications, audits);
        order.verify(users).findByIdForTicketUpdate(userId);
        order.verify(ledger).save(any(TicketLedger.class));
        order.verify(notifications).createAndEnqueue(notification.capture());
        order.verify(audits).recordTicketAdjustment(eq(adminId), eq(userId), anyString(),
                anyInt(), anyInt(), anyInt(), eq(reason));
        assertThat(notification.getValue().userId()).isEqualTo(userId);
        assertThat(notification.getValue().type()).isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);
        assertThat(notification.getValue().body()).contains(reason);
    }

    private void setBalance(int balance) {
        // Synthetic pre-existing balances also cover the negative balances allowed by IAP
        // refunds. Do not use User arithmetic helpers to create boundary-value fixtures.
        jdbc.update("UPDATE users SET tickets = ? WHERE id = ?", balance, userId);
    }

    private Map<String, List<List<String>>> databaseState() {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (String table : List.of("users", "ticket_ledger")) {
            result.put(table, jdbc.query("SELECT * FROM " + table + " ORDER BY id", (row, number) -> {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= row.getMetaData().getColumnCount(); column++) {
                    values.add(row.getString(column));
                }
                return values;
            }));
        }
        return result;
    }

    private User user(String nickname, UserRole role, int tickets) {
        return User.builder().provider(SocialProvider.KAKAO).socialId(UUID.randomUUID().toString())
                .email(nickname + "@example.test").nickname(nickname).role(role).status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL).createdAt(LocalDateTime.now()).tickets(tickets).build();
    }

    private static AdminRequests.TicketAdjustmentRequest request(Long userId, int amount, String reason) {
        return new AdminRequests.TicketAdjustmentRequest(UUID.randomUUID().toString(), userId, amount, reason);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.user.repository", "univ.airconnect.iap.repository",
            "univ.airconnect.admin"})
    @Import(AdminTicketAdjustmentService.class)
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:admin-ticket-adjustment-validation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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

        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    }
}
