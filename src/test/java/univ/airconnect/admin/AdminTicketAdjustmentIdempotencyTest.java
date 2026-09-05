package univ.airconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.RestAccessDeniedHandler;
import univ.airconnect.global.security.RestAuthenticationEntryPoint;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real claim/result, User, ticket history and audit persistence behind the actual service proxies.
 * A dedicated H2 database and explicit beans keep Boot configuration, environment files, external
 * databases, Redis, schedulers and notification delivery out of this test. Only NotificationService
 * is mocked, so invocation counts prove replay suppression, not external delivery atomicity.
 *
 * Fixtures commit before service calls; there is no enclosing rollback-only test transaction.
 * HTTP authorization starts at an authenticated principal and applies the production ADMIN rule,
 * not JWT cryptography/the entire production filter chain. H2 is not MySQL isolation parity.
 */
@SpringJUnitConfig(AdminTicketAdjustmentIdempotencyTest.IsolatedJpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class AdminTicketAdjustmentIdempotencyTest {
    private static final String ENDPOINT = "/api/v1/admin/tickets/adjustments";
    private static final String REASON = "synthetic compensation";
    private static final long WAIT_SECONDS = 10;

    @Autowired private AdminTicketAdjustmentService service;
    @Autowired private AdminTicketAdjustmentController controller;
    @Autowired private AdminTicketAdjustmentRepository operations;
    @Autowired private UserRepository users;
    @Autowired private TicketLedgerRepository history;
    @Autowired private AdminAuditLogRepository audits;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocalValidatorFactoryBean validator;
    @Autowired private FailingCommitTransactionManager transactionManager;
    @Autowired private ClaimInsertGate claimInsertGate;
    @Autowired private DataSource dataSource;
    @MockitoBean private NotificationService notifications;

    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private Long adminId;
    private Long otherAdminId;
    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
        inTransaction(() -> {
            adminId = users.saveAndFlush(user("operator", UserRole.ADMIN)).getId();
            otherAdminId = users.saveAndFlush(user("other-operator", UserRole.ADMIN)).getId();
            userId = users.saveAndFlush(user("recipient", UserRole.USER)).getId();
            otherUserId = users.saveAndFlush(user("other-recipient", UserRole.USER)).getId();
            return null;
        });
        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("adjustment-idempotency-fixture"), translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator).addFilters(security).build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        transactionManager.clearFailure();
        claimInsertGate.clear();
        for (FailurePoint point : FailurePoint.values()) {
            jdbc.execute("ALTER TABLE " + point.table + " DROP CONSTRAINT IF EXISTS " + point.constraint);
        }
        audits.deleteAllInBatch();
        operations.deleteAllInBatch();
        history.deleteAllInBatch();
        users.deleteAllInBatch();
        clearInvocations(notifications);
    }

    @Test
    void firstPostCommitsClaimBalanceHistoryResultAndAudit_andReplayAddsNothing() throws Exception {
        String operationId = newOperationId();
        var request = request(operationId, 5, "  " + REASON + "  ");

        var first = postSuccessfully(adminId, request);
        var replay = postSuccessfully(adminId, request(operationId, 5, REASON));
        var queried = getSuccessfully(adminId, operationId);

        assertCompleted(first, operationId, userId, 5, 10, 15);
        assertThat(replay).isEqualTo(first);
        assertThat(queried).isEqualTo(first);
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        assertThat(balance(adminId)).isEqualTo(10);
        assertHistory(first, REASON);
        AdminAuditLog audit = audits.findAll().get(0);
        assertThat(audit.getActorUserId()).isEqualTo(adminId);
        assertThat(audit.getAction()).isEqualTo(AdminAuditAction.TICKET_ADJUSTED);
        assertThat(audit.getTargetId()).isEqualTo(userId.toString());
        assertThat(audit.getReason()).isEqualTo(REASON);
        assertThat(metadata(audit).path("operationId").asText()).isEqualTo(operationId);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void distinctOperationsWithIdenticalPayloadAreDistinctAdjustments() {
        var first = service.adjust(adminId, request(newOperationId(), 5, REASON));
        var second = service.adjust(adminId, request(newOperationId(), 5, REASON));

        assertCompleted(first, first.operationId(), userId, 5, 10, 15);
        assertCompleted(second, second.operationId(), userId, 5, 15, 20);
        assertThat(second.ledgerId()).isNotEqualTo(first.ledgerId());
        assertCounts(2, 2, 2);
        assertContinuousHistory(20);
        verify(notifications, times(2)).createAndEnqueue(any());
    }

    enum Mismatch { USER, AMOUNT, REASON }

    @ParameterizedTest
    @EnumSource(Mismatch.class)
    void reusingOperationForAnotherCanonicalPayloadReturnsConflictWithoutChangingStoredOutcome(Mismatch mismatch)
            throws Exception {
        String operationId = newOperationId();
        var first = service.adjust(adminId, request(operationId, 5, REASON));
        var changed = new AdminRequests.TicketAdjustmentRequest(operationId,
                mismatch == Mismatch.USER ? otherUserId : userId,
                mismatch == Mismatch.AMOUNT ? 6 : 5,
                mismatch == Mismatch.REASON ? "another synthetic reason" : REASON);

        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data").isEmpty());

        assertThat(service.get(adminId, operationId)).isEqualTo(first);
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        assertThat(balance(otherUserId)).isEqualTo(10);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void simultaneousSameOperationUsesActualUniqueClaimAndBothCallersReceiveTheSameResult() {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        claimInsertGate.arm();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdminDtos.TicketAdjustmentResult> first = executor.submit(() -> service.adjust(adminId, request));
            Future<AdminDtos.TicketAdjustmentResult> second = executor.submit(() -> service.adjust(adminId, request));
            var firstResult = awaitFuture(first);
            var secondResult = awaitFuture(second);
            assertThat(claimInsertGate.arrivals()).isEqualTo(2);
            assertCompleted(firstResult, operationId, userId, 5, 10, 15);
            assertThat(secondResult).isEqualTo(firstResult);
            assertThat(service.get(adminId, operationId)).isEqualTo(firstResult);
        } finally {
            stopExecutor(executor);
            claimInsertGate.clear();
        }

        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void concurrentDifferentOperationsSerializeTheSameUserWithoutLosingEitherChange() {
        claimInsertGate.arm();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.adjust(adminId, request(newOperationId(), 5, REASON)));
            var second = executor.submit(() -> service.adjust(otherAdminId, request(newOperationId(), 3, REASON)));
            var firstResult = awaitFuture(first);
            var secondResult = awaitFuture(second);
            assertThat(firstResult.status()).isEqualTo("COMPLETED");
            assertThat(secondResult.status()).isEqualTo("COMPLETED");
            assertThat(firstResult.ledgerId()).isNotEqualTo(secondResult.ledgerId());
        } finally {
            stopExecutor(executor);
            claimInsertGate.clear();
        }
        assertCounts(2, 2, 2);
        assertContinuousHistory(18);
        verify(notifications, times(2)).createAndEnqueue(any());
    }

    @Test
    void lostSuccessResponseIsRecoveredFromOriginalResultEvenAfterCurrentBalanceChanges() throws Exception {
        String operationId = newOperationId();
        var original = service.adjust(adminId, request(operationId, 5, REASON));
        // Treat the first response as lost; another independent operation changes today's balance.
        service.adjust(adminId, request(newOperationId(), -2, "later synthetic correction"));
        assertThat(balance(userId)).isEqualTo(13);

        var recovered = getSuccessfully(adminId, operationId);
        var replay = postSuccessfully(adminId, request(operationId, 5, REASON));

        assertThat(recovered).isEqualTo(original);
        assertThat(replay).isEqualTo(original);
        assertThat(replay.afterTickets()).isEqualTo(15);
        assertThat(balance(userId)).isEqualTo(13);
        assertCounts(2, 2, 2);
        verify(notifications, times(2)).createAndEnqueue(any());
    }

    enum Rejection { OVERDRAFT, OVERFLOW, MISSING_USER }

    @ParameterizedTest
    @EnumSource(Rejection.class)
    void businessRejectionIsDurableHttpSuccessWithoutBalanceHistoryOrSuccessAudit(Rejection rejection)
            throws Exception {
        String operationId = newOperationId();
        if (rejection == Rejection.OVERFLOW) {
            jdbc.update("UPDATE users SET tickets = ? WHERE id = ?", Integer.MAX_VALUE, userId);
        }
        int initialBalance = balance(userId);
        var request = new AdminRequests.TicketAdjustmentRequest(operationId,
                rejection == Rejection.MISSING_USER ? Long.MAX_VALUE : userId,
                rejection == Rejection.OVERDRAFT ? -11 : 1, REASON);

        var rejected = postSuccessfully(adminId, request);
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.operationId()).isEqualTo(operationId);
        assertThat(rejected.ledgerId()).isNull();
        assertThat(rejected.completedAt()).isNotNull();
        assertThat(rejected.rejectionCode()).isEqualTo((rejection == Rejection.MISSING_USER
                ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_REQUEST).getCode());
        assertThat(rejected.rejectionMessage()).isNotBlank();
        assertThat(service.get(otherAdminId, operationId)).isEqualTo(rejected);
        assertThat(service.adjust(adminId, request)).isEqualTo(rejected);
        assertCounts(1, 0, 0);
        assertThat(balance(userId)).isEqualTo(initialBalance);
        verifyNoInteractions(notifications);
    }

    @Test
    void rejectedOperationDoesNotStartApplyingAfterItsInsufficientBalanceHasBeenFixed() {
        var request = request(newOperationId(), -11, REASON);
        var rejected = service.adjust(adminId, request);
        assertThat(rejected.status()).isEqualTo("REJECTED");
        service.adjust(adminId, request(newOperationId(), 10, "later synthetic credit"));

        assertThat(service.adjust(adminId, request)).isEqualTo(rejected);
        assertThat(service.get(adminId, request.operationId())).isEqualTo(rejected);
        assertThat(balance(userId)).isEqualTo(20);
        assertCounts(2, 1, 1);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    enum FailurePoint {
        HISTORY("ticket_ledger", "ck_idempotency_history_failure", "reason <> 'ADMIN:synthetic compensation'"),
        RESULT("admin_ticket_adjustments", "ck_idempotency_result_failure", "status <> 'COMPLETED'"),
        AUDIT("admin_audit_logs", "ck_idempotency_audit_failure", "action <> 'TICKET_ADJUSTED'");

        final String table;
        final String constraint;
        final String expression;
        FailurePoint(String table, String constraint, String expression) {
            this.table = table;
            this.constraint = constraint;
            this.expression = expression;
        }
    }

    @ParameterizedTest
    @EnumSource(FailurePoint.class)
    void realDatabaseFailureRollsBackClaimBalanceHistoryResultAndAudit_andSameIdCanRetry(FailurePoint failure) {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        jdbc.execute("ALTER TABLE " + failure.table + " ADD CONSTRAINT " + failure.constraint
                + " CHECK (" + failure.expression + ")");
        try {
            assertThatThrownBy(() -> service.adjust(adminId, request)).isInstanceOf(RuntimeException.class);
            assertCounts(0, 0, 0);
            assertThat(balance(userId)).isEqualTo(10);
            assertNotFound(operationId);
        } finally {
            jdbc.execute("ALTER TABLE " + failure.table + " DROP CONSTRAINT " + failure.constraint);
        }

        clearInvocations(notifications);
        var retry = service.adjust(adminId, request);
        assertCompleted(retry, operationId, userId, 5, 10, 15);
        assertCounts(1, 1, 1);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void failureAtCommitAfterRealFlushLeavesNoStoredOutcomeAndAllowsSameIdRetry() {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        transactionManager.failNextWriteCommit();

        assertThatThrownBy(() -> service.adjust(adminId, request)).isInstanceOf(RuntimeException.class);
        assertThat(transactionManager.failedCommits()).isEqualTo(1);
        assertCounts(0, 0, 0);
        assertThat(balance(userId)).isEqualTo(10);
        assertNotFound(operationId);

        clearInvocations(notifications);
        var retried = service.adjust(adminId, request);
        assertCompleted(retried, operationId, userId, 5, 10, 15);
        assertCounts(1, 1, 1);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    enum InvalidOperation { NULL, EMPTY, MALFORMED, SHORT_UUID }

    @ParameterizedTest
    @EnumSource(InvalidOperation.class)
    void malformedOperationIdIsBadRequestAndNeverClaimsAnOperation(InvalidOperation invalid) throws Exception {
        String operationId = switch (invalid) {
            case NULL -> null;
            case EMPTY -> "";
            case MALFORMED -> "synthetic-not-a-uuid";
            case SHORT_UUID -> "1-1-1-1-1";
        };
        var request = request(operationId, 5, REASON);
        assertThatThrownBy(() -> service.adjust(adminId, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").isEmpty());
        assertCounts(0, 0, 0);
        assertThat(balance(userId)).isEqualTo(10);
        verifyNoInteractions(notifications);
    }

    @Test
    void missingOperationLookupIsReadOnlyAndReturnsNotFound() throws Exception {
        String operationId = newOperationId();
        assertNotFound(operationId);
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(ENDPOINT + "/{operationId}", operationId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").isEmpty());
        assertCounts(0, 0, 0);
        assertThat(balance(userId)).isEqualTo(10);
        verifyNoInteractions(notifications);
    }

    @Test
    void resultLookupReturnsOnlyReceiptFieldsWithoutRequestReasonOrPersonalInformation() throws Exception {
        String operationId = newOperationId();
        service.adjust(adminId, request(operationId, 5, REASON));
        authenticate(adminId, UserRole.ADMIN);
        var response = mvc.perform(get(ENDPOINT + "/{operationId}", operationId))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var payload = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data");
        java.util.Set<String> fields = new java.util.HashSet<>();
        payload.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("operationId", "status", "userId", "amount",
                "beforeTickets", "afterTickets", "ledgerId", "completedAt", "rejectionCode", "rejectionMessage");
        assertThat(payload.toString()).doesNotContain(REASON, "requestHash", "email", "nickname", "actorUserId");
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void anotherCurrentAdministratorMayLookUpAndReplayTheOriginalOperation() throws Exception {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        var original = service.adjust(adminId, request);

        assertThat(getSuccessfully(otherAdminId, operationId)).isEqualTo(original);
        assertThat(postSuccessfully(otherAdminId, request)).isEqualTo(original);
        assertCounts(1, 1, 1);
        assertThat(audits.findAll().get(0).getActorUserId()).isEqualTo(adminId);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void anonymousAndOrdinaryUsersCannotSubmitOrLookUpEvenAKnownOperation() throws Exception {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        var original = service.adjust(adminId, request);
        for (boolean authenticated : List.of(false, true)) {
            if (authenticated) authenticate(userId, UserRole.USER);
            else SecurityContextHolder.clearContext();
            mvc.perform(get(ENDPOINT + "/{operationId}", operationId))
                    .andExpect(authenticated ? status().isForbidden() : status().isUnauthorized())
                    .andExpect(jsonPath("$.data").isEmpty());
            if (authenticated) authenticate(userId, UserRole.USER);
            else SecurityContextHolder.clearContext();
            mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(authenticated ? status().isForbidden() : status().isUnauthorized())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
        assertForbidden(() -> service.get(userId, operationId));
        assertForbidden(() -> service.adjust(userId, request));
        assertForbidden(() -> service.get(null, operationId));
        assertForbidden(() -> service.adjust(null, request));
        assertThat(service.get(adminId, operationId)).isEqualTo(original);
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void staleAdminPrincipalCannotLookUpOrReplayAfterDatabaseRoleWasDowngraded() throws Exception {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        service.adjust(adminId, request);
        inTransaction(() -> {
            users.findById(adminId).orElseThrow().changeRole(UserRole.USER);
            return null;
        });
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(ENDPOINT + "/{operationId}", operationId))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"RESTRICTED", "SUSPENDED", "DELETED"})
    void inactiveAdministratorCannotLookUpOrReplayStoredResult(UserStatus status) {
        String operationId = newOperationId();
        var request = request(operationId, 5, REASON);
        service.adjust(adminId, request);
        inTransaction(() -> {
            ReflectionTestUtils.setField(users.findById(adminId).orElseThrow(), "status", status);
            return null;
        });

        assertForbidden(() -> service.get(adminId, operationId));
        assertForbidden(() -> service.adjust(adminId, request));
        assertCounts(1, 1, 1);
        assertThat(balance(userId)).isEqualTo(15);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    private AdminRequests.TicketAdjustmentRequest request(String operationId, int amount, String reason) {
        return new AdminRequests.TicketAdjustmentRequest(operationId, userId, amount, reason);
    }

    private AdminDtos.TicketAdjustmentResult postSuccessfully(Long actorId, AdminRequests.TicketAdjustmentRequest request)
            throws Exception {
        // FilterChainProxy clears the SecurityContext after every request.
        authenticate(actorId, UserRole.ADMIN);
        String json = mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.treeToValue(objectMapper.readTree(json).path("data"), AdminDtos.TicketAdjustmentResult.class);
    }

    private AdminDtos.TicketAdjustmentResult getSuccessfully(Long actorId, String operationId) throws Exception {
        authenticate(actorId, UserRole.ADMIN);
        String json = mvc.perform(get(ENDPOINT + "/{operationId}", operationId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.treeToValue(objectMapper.readTree(json).path("data"), AdminDtos.TicketAdjustmentResult.class);
    }

    private void assertCompleted(AdminDtos.TicketAdjustmentResult result, String operationId, Long targetId,
                                 int amount, int before, int after) {
        assertThat(result.operationId()).isEqualTo(operationId);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.userId()).isEqualTo(targetId);
        assertThat(result.amount()).isEqualTo(amount);
        assertThat(result.beforeTickets()).isEqualTo(before);
        assertThat(result.afterTickets()).isEqualTo(after);
        assertThat(result.ledgerId()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.rejectionCode()).isNull();
        assertThat(result.rejectionMessage()).isNull();
    }

    private void assertHistory(AdminDtos.TicketAdjustmentResult result, String reason) {
        TicketLedger row = history.findById(result.ledgerId()).orElseThrow();
        assertThat(row.getUserId()).isEqualTo(result.userId());
        assertThat(row.getRefType()).isEqualTo(LedgerRefType.ADMIN_ADJUSTMENT);
        assertThat(row.getRefId()).contains(result.operationId());
        assertThat(row.getReason()).isEqualTo("ADMIN:" + reason);
        assertThat(row.getChangeAmount()).isEqualTo(result.amount());
        assertThat(row.getBeforeAmount()).isEqualTo(result.beforeTickets());
        assertThat(row.getAfterAmount()).isEqualTo(result.afterTickets());
    }

    private void assertContinuousHistory(int expectedBalance) {
        int previousBalance = 10;
        for (TicketLedger row : history.findAll(Sort.by("id"))) {
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getBeforeAmount()).isEqualTo(previousBalance);
            previousBalance += row.getChangeAmount();
            assertThat(row.getAfterAmount()).isEqualTo(previousBalance);
        }
        assertThat(previousBalance).isEqualTo(expectedBalance);
        assertThat(balance(userId)).isEqualTo(expectedBalance);
    }

    private void assertCounts(long operationCount, long historyCount, long auditCount) {
        assertThat(operations.count()).isEqualTo(operationCount);
        assertThat(history.count()).isEqualTo(historyCount);
        assertThat(audits.count()).isEqualTo(auditCount);
    }

    private void assertNotFound(String operationId) {
        assertThatThrownBy(() -> service.get(adminId, operationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private int balance(Long targetId) {
        return jdbc.queryForObject("SELECT tickets FROM users WHERE id = ?", Integer.class, targetId);
    }

    private JsonNode metadata(AdminAuditLog audit) throws Exception {
        JsonNode node = objectMapper.readTree(audit.getMetadataJson());
        return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
    }

    private void authenticate(Long actorId, UserRole role) {
        var principal = new CustomUserPrincipal(actorId, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private User user(String nickname, UserRole role) {
        return User.builder().provider(SocialProvider.APPLE).socialId("idempotency-fixture-" + UUID.randomUUID())
                .nickname(nickname).role(role).status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                .tickets(10).createdAt(LocalDateTime.now()).build();
    }

    private String newOperationId() { return UUID.randomUUID().toString(); }

    private <T> T inTransaction(Supplier<T> action) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction.execute(tx -> action.get());
    }

    private static <T> T awaitFuture(Future<T> future) {
        try {
            return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for adjustment", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("Adjustment worker failed", exception.getCause());
        } catch (TimeoutException exception) {
            throw new AssertionError("Adjustment exceeded bounded wait", exception);
        }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stopping adjustment workers", exception);
        }
    }

    /** Gates SQL only; real repository persist/flush and exception translation stay intact. */
    static class ClaimInsertGate implements StatementInspector {
        private volatile CyclicBarrier barrier;
        private final AtomicInteger arrivals = new AtomicInteger();

        void arm() {
            arrivals.set(0);
            barrier = new CyclicBarrier(2);
        }

        void clear() { barrier = null; }
        int arrivals() { return arrivals.get(); }

        @Override
        public String inspect(String sql) {
            CyclicBarrier active = barrier;
            if (active != null && sql.stripLeading().toLowerCase(Locale.ROOT)
                    .startsWith("insert into admin_ticket_adjustments") && arrivals.incrementAndGet() <= 2) {
                try {
                    active.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted before real claim insert", exception);
                } catch (Exception exception) {
                    throw new AssertionError("Both claim INSERTs must reach the real database", exception);
                }
            }
            return sql;
        }
    }

    static class FailingCommitTransactionManager extends JpaTransactionManager {
        private final AtomicBoolean failCommit = new AtomicBoolean();
        private final AtomicInteger failedCommits = new AtomicInteger();

        FailingCommitTransactionManager(EntityManagerFactory factory) { super(factory); }
        void failNextWriteCommit() { failCommit.set(true); }
        void clearFailure() { failCommit.set(false); failedCommits.set(0); }
        int failedCommits() { return failedCommits.get(); }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (!status.isReadOnly() && failCommit.compareAndSet(true, false)) {
                var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());
                assertThat(entityManager).isNotNull();
                entityManager.flush();
                super.doRollback(status);
                failedCommits.incrementAndGet();
                throw new TransactionSystemException("isolated adjustment commit failure after real flush");
            }
            super.doCommit(status);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.admin", "univ.airconnect.user.repository",
            "univ.airconnect.iap.repository"})
    @Import({AdminTicketAdjustmentService.class, AdminTicketAdjustmentController.class, AdminAuditLogService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:admin-ticket-idempotency-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
                    "sa", "");
        }

        @Bean ClaimInsertGate claimInsertGate() { return new ClaimInsertGate(); }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                                                                          ClaimInsertGate gate) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC", "hibernate.session_factory.statement_inspector", gate));
            return factory;
        }

        @Bean FailingCommitTransactionManager transactionManager(EntityManagerFactory factory) {
            return new FailingCommitTransactionManager(factory);
        }

        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    }
}
