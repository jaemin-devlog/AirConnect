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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.chat.domain.entity.ChatMessage;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
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
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.domain.ReportReasonCode;
import univ.airconnect.moderation.domain.ReportSourceType;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.dto.response.UserReportResponse;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.notification.service.NotificationPreferenceService;
import univ.airconnect.notification.service.PushDeviceService;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real report/chat/user/matching/audit repositories and service transaction proxies in a dedicated
 * in-memory H2 database. No Boot application, environment or production resources, Redis, scheduler,
 * mail or push transport is loaded. Most notification assertions stop at the mocked boundary;
 * one test delegates to the real notification implementation and stores real notification/outbox
 * rows in the report transaction. Neither setup delivers anything. Fixtures commit before calls.
 * HTTP checks apply the production ADMIN rule to an authenticated principal, not the JWT pipeline.
 * H2 exercises SQL/transaction regressions and is not a claim of MySQL isolation equivalence.
 */
@SpringJUnitConfig(AdminReportHandlingSecurityTest.IsolatedJpaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class AdminReportHandlingSecurityTest {
    private static final String ENDPOINT = "/api/v1/admin/reports";
    private static final String MEMO = "internal-only investigative note fixture";
    private static final String REPLY = "public reporter response fixture";
    private static final String BODY = "deleted original report evidence fixture";
    private static final String OTHER_BODY = "unrelated room body fixture";
    private static final long WAIT_SECONDS = 10;

    @Autowired private AdminReportService service;
    @Autowired private AdminReportController controller;
    @Autowired private AdminController adminController;
    @Autowired private AdminService adminService;
    @Autowired private UserReportRepository reports;
    @Autowired private UserRepository users;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatRoomMemberRepository members;
    @Autowired private ChatMessageRepository messages;
    @Autowired private MatchingConnectionRepository connections;
    @Autowired private AdminAuditLogRepository audits;
    @Autowired private NotificationRepository notificationRows;
    @Autowired private NotificationOutboxRepository outboxRows;
    @Autowired private PushDeviceRepository deviceRows;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocalValidatorFactoryBean validator;
    @Autowired private JpaTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private SqlProbe sqlProbe;
    @MockitoBean private IapOrderRepository orders;
    @MockitoBean private TicketLedgerRepository tickets;
    @MockitoBean private AnalyticsEventRepository analytics;
    @MockitoBean private UserService userService;
    @MockitoBean private NotificationService notifications;
    @MockitoBean private StatisticsService statistics;
    @MockitoBean private AdminOperationsService adminOperations;
    @MockitoBean private AdminUserPurgeService purge;

    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private Long adminId, reporterId, subjectId, outsiderId, reportId;
    private Long roomId, otherRoomId, messageId, wrongAuthorMessageId, otherMessageId;
    private Long connectionId, otherConnectionId;
    private LocalDateTime messageTime;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanUp();
        messageTime = LocalDateTime.now(Clock.systemUTC()).withNano(0).minusHours(1);
        inTransaction(() -> {
            User admin = users.saveAndFlush(user("operator", UserRole.ADMIN));
            User reporter = users.saveAndFlush(user("reporter", UserRole.USER));
            User subject = users.saveAndFlush(user("subject", UserRole.USER));
            User outsider = users.saveAndFlush(user("outsider", UserRole.USER));
            adminId = admin.getId(); reporterId = reporter.getId();
            subjectId = subject.getId(); outsiderId = outsider.getId();
            ChatRoom room = rooms.saveAndFlush(ChatRoom.create("report evidence", ChatRoomType.GROUP));
            ChatRoom otherRoom = rooms.saveAndFlush(ChatRoom.create("unrelated room", ChatRoomType.GROUP));
            roomId = room.getId(); otherRoomId = otherRoom.getId();
            ChatRoomMember hiddenReporter = ChatRoomMember.create(room, reporter);
            hiddenReporter.hide("synthetic-block");
            members.save(hiddenReporter);
            members.save(ChatRoomMember.create(room, subject));
            members.save(ChatRoomMember.create(otherRoom, subject));
            members.save(ChatRoomMember.create(otherRoom, outsider));
            messageId = saveMessage(roomId, subjectId, BODY, true);
            wrongAuthorMessageId = saveMessage(roomId, outsiderId, "wrong author fixture", false);
            otherMessageId = saveMessage(otherRoomId, subjectId, OTHER_BODY, false);
            MatchingConnection connection = MatchingConnection.createPending(reporterId, subjectId);
            connection.accept(roomId);
            connectionId = connections.saveAndFlush(connection).getId();
            MatchingConnection other = MatchingConnection.createPending(outsiderId, subjectId);
            other.accept(otherRoomId);
            otherConnectionId = connections.saveAndFlush(other).getId();
            reportId = reports.saveAndFlush(report(subjectId, ReportSourceType.PROFILE, subjectId.toString())).getId();
            return null;
        });
        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("report-handling-fixture"), translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(controller, adminController)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator).addFilters(security).build();
        sqlProbe.clear();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        sqlProbe.clear();
        for (String table : List.of("user_reports", "admin_audit_logs")) {
            jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS ck_report_fixture_failure");
        }
        outboxRows.deleteAllInBatch(); notificationRows.deleteAllInBatch(); deviceRows.deleteAllInBatch();
        audits.deleteAllInBatch(); reports.deleteAllInBatch();
        members.deleteAllInBatch(); messages.deleteAllInBatch(); rooms.deleteAllInBatch();
        connections.deleteAllInBatch(); users.deleteAllInBatch();
        reset(notifications);
    }

    @Test
    void memoAndReplyOnlyEditIsVersionedAndPrivateWithoutNotification() throws Exception {
        var result = updateHttp(reportId, change(0, ReportStatus.OPEN, "  " + MEMO + "  ", "  " + REPLY + "  "));
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.internalMemo()).isEqualTo(MEMO);
        assertThat(result.reporterReply()).isEqualTo(REPLY);
        assertThat(result.handledByUserId()).isEqualTo(adminId);
        var beforeRead = reportState();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(ENDPOINT + "/{id}", reportId)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.internalMemo").value(MEMO));
        assertThat(reportState()).isEqualTo(beforeRead);
        verifyNoInteractions(notifications);
        assertThat(audits.count()).isEqualTo(1);
        var audit = audits.findAll().get(0);
        assertNoPrivateAuditText(audit);
        assertThat(auditMetadata(audit).path("internalMemoChanged").asBoolean()).isTrue();
        assertThat(auditMetadata(audit).path("reporterReplyChanged").asBoolean()).isTrue();
        String userJson = objectMapper.writeValueAsString(UserReportResponse.from(storedReport()));
        String listJson = objectMapper.writeValueAsString(adminService.getReports(0, 20, null, null));
        assertThat(userJson).doesNotContain(MEMO, "internalMemo", "reporterReply", "handledByUserId");
        assertThat(listJson).doesNotContain(MEMO, "internalMemo");
    }

    @ParameterizedTest
    @EnumSource(ReportStatus.class)
    void onlyActualStatusTransitionSendsTheSpecifiedReporterNotification(ReportStatus next) {
        if (next == ReportStatus.OPEN) {
            inTransaction(() -> { reports.findById(reportId).orElseThrow().updateStatus(ReportStatus.IN_REVIEW); return null; });
        }
        Long version = storedReport().getVersion();
        var result = service.update(adminId, reportId, change(version, next, MEMO, REPLY));
        assertThat(result.version()).isEqualTo(version + 1);
        assertThat(result.record().status()).isEqualTo(next);
        assertThat(audits.count()).isEqualTo(1);
        assertNoPrivateAuditText(audits.findAll().get(0));
        if (next == ReportStatus.OPEN) {
            verifyNoInteractions(notifications);
        } else {
            var command = ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
            verify(notifications).createAndEnqueue(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(reporterId);
            assertThat(command.getValue().body()).doesNotContain(MEMO);
            assertThat(command.getValue().payloadJson()).doesNotContain(MEMO, "internalMemo");
            if (next == ReportStatus.IN_REVIEW) {
                assertThat(command.getValue().body()).isEqualTo("신고가 검토중입니다. 빠른 시일 내에 처리됩니다.");
            } else {
                assertThat(command.getValue().body()).contains(REPLY);
            }
        }
    }

    @Test
    void changingOnlyReplyOnAlreadyResolvedReportDoesNotNotifyAgain() {
        var first = service.update(adminId, reportId, change(0, ReportStatus.RESOLVED, MEMO, REPLY));
        clearInvocations(notifications);
        var second = service.update(adminId, reportId,
                change(first.version(), ReportStatus.RESOLVED, MEMO, "edited public response"));
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(second.reporterReply()).isEqualTo("edited public response");
        verifyNoInteractions(notifications);
        assertThat(audits.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"RESOLVED", "REJECTED"})
    void terminalMemoEditPreservesCompletionTimeAndAverageProcessingSeconds(ReportStatus terminal) {
        LocalDateTime completed = prepareTerminalFixture(terminal, false);
        assertThat(reports.averageProcessingSeconds()).isEqualTo(3600.0);
        Long version = storedReport().getVersion();

        service.update(adminId, reportId, change(version, terminal, MEMO, REPLY));

        assertThat(storedReport().getCompletedAt()).isEqualTo(completed);
        assertThat(storedReport().getUpdatedAt()).isAfter(completed);
        assertThat(reports.averageProcessingSeconds()).isEqualTo(3600.0);
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"RESOLVED", "REJECTED"})
    void legacyTerminalMemoEditPreservesTheObservedOldUpdatedAtAsCompletion(ReportStatus terminal) {
        LocalDateTime observedCompletion = prepareTerminalFixture(terminal, true);
        assertThat(storedReport().getCompletedAt()).isNull();
        assertThat(reports.averageProcessingSeconds()).isEqualTo(3600.0);

        service.update(adminId, reportId, change(storedReport().getVersion(), terminal, MEMO, REPLY));

        assertThat(storedReport().getCompletedAt()).isEqualTo(observedCompletion);
        assertThat(reports.averageProcessingSeconds()).isEqualTo(3600.0);
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus.class, names = {"OPEN", "IN_REVIEW"})
    void reopeningACompletedReportClearsCompletionTime(ReportStatus reopened) {
        prepareTerminalFixture(ReportStatus.RESOLVED, false);
        service.update(adminId, reportId, change(storedReport().getVersion(), reopened, MEMO, REPLY));
        assertThat(storedReport().getStatus()).isEqualTo(reopened);
        assertThat(storedReport().getCompletedAt()).isNull();
        assertThat(reports.averageProcessingSeconds()).isZero();
    }

    enum InvalidInput { NO_VERSION, NEGATIVE_VERSION, NO_STATUS, LONG_MEMO, LONG_REPLY, RESOLVED_NO_REPLY, REJECTED_BLANK_REPLY }

    @ParameterizedTest
    @EnumSource(InvalidInput.class)
    void invalidInputIsRejectedWithoutChangingReportOrAudit(InvalidInput invalid) throws Exception {
        var request = new AdminRequests.ReportStatusUpdateRequest(
                invalid == InvalidInput.NO_VERSION ? null : invalid == InvalidInput.NEGATIVE_VERSION ? -1L : 0L,
                invalid == InvalidInput.NO_STATUS ? null : invalid == InvalidInput.RESOLVED_NO_REPLY ? ReportStatus.RESOLVED
                        : invalid == InvalidInput.REJECTED_BLANK_REPLY ? ReportStatus.REJECTED : ReportStatus.OPEN,
                invalid == InvalidInput.LONG_MEMO ? "m".repeat(1001) : MEMO,
                invalid == InvalidInput.LONG_REPLY ? "r".repeat(301)
                        : invalid == InvalidInput.RESOLVED_NO_REPLY ? null : invalid == InvalidInput.REJECTED_BLANK_REPLY ? " \t " : REPLY);
        var before = reportState();
        assertError(() -> service.update(adminId, reportId, request), ErrorCode.INVALID_REQUEST);
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(patch(ENDPOINT + "/{id}", reportId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(reportState()).isEqualTo(before);
        assertThat(audits.count()).isZero();
        verifyNoInteractions(notifications);
    }

    @Test
    void maximumTrimmedTextLengthsAreAccepted() throws Exception {
        var result = updateHttp(reportId, change(0, ReportStatus.RESOLVED, "  " + "가".repeat(1000) + "  ",
                "  " + "나".repeat(300) + "  "));
        assertThat(result.internalMemo()).hasSize(1000);
        assertThat(result.reporterReply()).hasSize(300);
        assertThat(storedReport().getInternalMemo()).hasSize(1000);
    }

    @Test
    void retryingTheSameVersionReturns409WithoutRepeatingNotificationOrAudit() throws Exception {
        var request = change(0, ReportStatus.IN_REVIEW, MEMO, REPLY);
        updateHttp(reportId, request);
        var before = reportState();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(patch(ENDPOINT + "/{id}", reportId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(reportState()).isEqualTo(before);
        assertThat(audits.count()).isEqualTo(1);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    @Test
    void concurrentSameVersionHasOneWinnerAndOneConflictUsingRealReportLock() {
        sqlProbe.armReportLockRace();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptChange("first"));
            Future<Boolean> second = executor.submit(() -> attemptChange("second"));
            assertThat(List.of(awaitFuture(first), awaitFuture(second))).containsExactlyInAnyOrder(true, false);
            assertThat(sqlProbe.lockArrivals()).isEqualTo(2);
        } finally {
            stopExecutor(executor);
            sqlProbe.clear();
        }
        UserReport stored = storedReport();
        assertThat(stored.getVersion()).isEqualTo(1);
        assertThat(stored.getInternalMemo()).isIn("first", "second");
        assertThat(stored.getReporterReply()).isEqualTo(stored.getInternalMemo() + " reply");
        assertThat(audits.count()).isEqualTo(1);
        verify(notifications, times(1)).createAndEnqueue(any());
    }

    enum FailurePoint { REPORT_UPDATE, AUDIT_INSERT, NOTIFICATION_BOUNDARY }

    @ParameterizedTest
    @EnumSource(FailurePoint.class)
    void reportAuditAndVersionRollBackOnFailure(FailurePoint point) {
        var before = reportState();
        if (point == FailurePoint.REPORT_UPDATE) {
            jdbc.execute("ALTER TABLE user_reports ADD CONSTRAINT ck_report_fixture_failure CHECK (internal_memo <> '" + MEMO + "')");
        } else if (point == FailurePoint.AUDIT_INSERT) {
            rejectAuditAction("REPORT_STATUS_UPDATED");
        } else {
            doThrow(new IllegalStateException("synthetic notification boundary failure"))
                    .when(notifications).createAndEnqueue(any());
        }
        assertThatThrownBy(() -> service.update(adminId, reportId, change(0, ReportStatus.IN_REVIEW, MEMO, REPLY)))
                .isInstanceOf(RuntimeException.class);
        assertThat(reportState()).isEqualTo(before);
        assertThat(audits.count()).isZero();
    }

    @Test
    void auditFailureRollsBackActuallyFlushedNotificationAndOutboxAlongWithReport() {
        PushDevice device = inTransaction(() -> deviceRows.saveAndFlush(PushDevice.register(reporterId,
                "synthetic-report-device", PushPlatform.ANDROID, PushProvider.FCM,
                "synthetic-not-a-real-push-token", null, true, "fixture", "fixture", "ko", "UTC", LocalDateTime.now())));
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        PushDeviceService devices = mock(PushDeviceService.class);
        when(preferences.getDeliveryPolicy(reporterId, NotificationType.SYSTEM_ANNOUNCEMENT))
                .thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
        when(devices.findPushableDevices(reporterId)).thenReturn(List.of(device));
        NotificationService realNotifications = new NotificationService(notificationRows, outboxRows,
                preferences, devices, objectMapper);
        AtomicBoolean wroteBothRows = new AtomicBoolean();
        // The real implementation runs inside the report service's existing transaction;
        // only preference/device selection are synthetic, and no delivery worker is loaded.
        doAnswer(invocation -> {
            var result = realNotifications.createAndEnqueue(invocation.getArgument(0));
            notificationRows.flush(); outboxRows.flush();
            assertThat(notificationRows.count()).isEqualTo(1);
            assertThat(outboxRows.count()).isEqualTo(1);
            wroteBothRows.set(true);
            return result;
        }).when(notifications).createAndEnqueue(any());
        var before = reportState();
        rejectAuditAction("REPORT_STATUS_UPDATED");

        assertThatThrownBy(() -> service.update(adminId, reportId, change(0, ReportStatus.IN_REVIEW, MEMO, REPLY)))
                .isInstanceOf(RuntimeException.class);

        assertThat(wroteBothRows).isTrue();
        assertThat(reportState()).isEqualTo(before);
        assertThat(notificationRows.count()).isZero();
        assertThat(outboxRows.count()).isZero();
        assertThat(audits.count()).isZero();
    }

    enum EvidenceCase {
        PROFILE, WRONG_PROFILE, MISSING_PROFILE, MESSAGE, WRONG_AUTHOR, OUTSIDE_MESSAGE, MISSING_MESSAGE,
        ROOM, OUTSIDE_ROOM, MISSING_SUBJECT_MEMBERSHIP, MISSING_ROOM,
        MATCHING, OUTSIDE_MATCHING, MATCHING_WITHOUT_VALID_ROOM, MISSING_MATCHING,
        NULL_SOURCE, MALFORMED_SOURCE, ZERO_SOURCE, NEGATIVE_SOURCE, OVERFLOW_SOURCE, OTHER_SOURCE
    }

    @ParameterizedTest
    @EnumSource(EvidenceCase.class)
    void evidenceRequiresTheExactStoredRelationshipAndNeverReturnsRawSourceOrMessageBody(EvidenceCase fixture)
            throws Exception {
        EvidenceInput input = evidenceInput(fixture);
        Long targetReport = createReport(input.subject(), input.type(), input.source());
        sqlProbe.messageSelects.clear();
        var before = conversationState();
        var detail = service.get(adminId, targetReport);
        assertThat(detail.evidence().status()).isEqualTo(input.available() ? "AVAILABLE" : "UNAVAILABLE");
        assertThat(detail.evidence().sourceType()).isEqualTo(input.type());
        if (input.available()) {
            assertThat(detail.evidence().userId()).isEqualTo(subjectId);
            assertThat(detail.evidence().roomId()).isEqualTo(input.room());
            assertThat(detail.evidence().connectionId()).isEqualTo(input.connection());
            assertThat(detail.evidence().messageId()).isEqualTo(input.message());
        } else {
            assertThat(detail.evidence().userId()).isNull();
            assertThat(detail.evidence().roomId()).isNull();
            assertThat(detail.evidence().connectionId()).isNull();
            assertThat(detail.evidence().messageId()).isNull();
        }
        String json = objectMapper.writeValueAsString(detail);
        assertThat(json).doesNotContain("sourceId", "content", BODY, OTHER_BODY, "https://outside.example.test");
        if (input.type() == ReportSourceType.CHAT_MESSAGE && input.source() != null) {
            assertThat(sqlProbe.messageSelects).allSatisfy(sql -> assertThat(sql).doesNotContain(".content", ".message"));
        }
        assertThat(conversationState()).isEqualTo(before);
        assertThat(audits.count()).isZero();
        verifyNoInteractions(notifications);
    }

    @Test
    void automaticEvidenceHistoryRevalidatesReportRoomOnEveryPage() throws Exception {
        Long target = createReport(subjectId, ReportSourceType.CHAT_MESSAGE, messageId.toString());
        var before = conversationState();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(post(ENDPOINT + "/{id}/evidence-history", target).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminRequests.ReportHistoryRequest(roomId, null, 1))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(conversationState()).isEqualTo(before);
        assertError(() -> service.readEvidenceHistory(adminId, target,
                new AdminRequests.ReportHistoryRequest(otherRoomId, null, 1), "fixture"), ErrorCode.FORBIDDEN);
        removeMembership(reporterId);
        assertError(() -> service.readEvidenceHistory(adminId, target,
                new AdminRequests.ReportHistoryRequest(roomId, messageId, 1), "fixture"), ErrorCode.FORBIDDEN);
    }

    @Test
    void explicitEvidenceInspectionReturnsDeletedOriginalOnlyAfterAuditAndDoesNotMarkRead() throws Exception {
        Long targetReport = createReport(subjectId, ReportSourceType.CHAT_MESSAGE, messageId.toString());
        var before = conversationState();
        assertThat(service.get(adminId, targetReport).evidence().status()).isEqualTo("AVAILABLE");
        authenticate(adminId, UserRole.ADMIN);
        String json = mvc.perform(post(ENDPOINT + "/{id}/evidence-inspections", targetReport)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inspection(roomId))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode items = objectMapper.readTree(json).path("data").path("messages").path("items");
        List<JsonNode> originals = new ArrayList<>();
        items.forEach(item -> { if (item.path("messageId").asLong() == messageId) originals.add(item); });
        assertThat(originals).hasSize(1);
        assertThat(originals.get(0).path("content").asText()).isEqualTo(BODY);
        assertThat(originals.get(0).path("deleted").asBoolean()).isTrue();
        assertThat(json).doesNotContain(OTHER_BODY);
        assertThat(audits.countByAction(AdminAuditAction.CHAT_MESSAGES_INSPECTED)).isEqualTo(1);
        assertThat(audits.findAll().get(0).getMetadataJson()).doesNotContain(BODY, OTHER_BODY);
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test
    void inspectionRejectsAnotherRoomWrongReasonAndPreviouslyAvailableButNowBrokenRelationship() {
        Long targetReport = createReport(subjectId, ReportSourceType.CHAT_MESSAGE, messageId.toString());
        assertThat(service.get(adminId, targetReport).evidence().status()).isEqualTo("AVAILABLE");
        assertError(() -> service.inspectEvidence(adminId, targetReport, inspection(otherRoomId), "fixture"), ErrorCode.FORBIDDEN);
        var wrongReason = new AdminRequests.ReportEvidenceInspectionRequest(roomId,
                AdminRequests.ChatInspectionReason.USER_SUPPORT, messageTime.minusMinutes(1), messageTime.plusMinutes(5), 0, 50);
        assertError(() -> service.inspectEvidence(adminId, targetReport, wrongReason, "fixture"), ErrorCode.INVALID_REQUEST);
        removeMembership(reporterId);
        assertError(() -> service.inspectEvidence(adminId, targetReport, inspection(roomId), "fixture"), ErrorCode.FORBIDDEN);
        assertThat(audits.count()).isZero();
    }

    @Test
    void evidenceInspectionAuditDatabaseFailureFailsClosedWithoutBodyOrReadMutation() throws Exception {
        Long targetReport = createReport(subjectId, ReportSourceType.CHAT_MESSAGE, messageId.toString());
        var before = conversationState();
        rejectAuditAction("CHAT_MESSAGES_INSPECTED");
        authenticate(adminId, UserRole.ADMIN);
        String json = mvc.perform(post(ENDPOINT + "/{id}/evidence-inspections", targetReport)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inspection(roomId))))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.data").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).doesNotContain(BODY, OTHER_BODY);
        assertThat(audits.count()).isZero();
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test
    void linkedActionMustMatchReportSubjectBeforeAnyMutation() {
        var request = new AdminRequests.UserActionRequest(AdminRequests.UserActionType.SUSPEND,
                "synthetic moderation reason", LocalDateTime.now().plusDays(1), reportId);
        assertError(() -> adminService.applyUserAction(adminId, outsiderId, request), ErrorCode.INVALID_REQUEST);
        assertThat(users.findById(outsiderId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findById(subjectId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(audits.count()).isZero();
        verifyNoInteractions(notifications, userService);
    }

    @Test
    void linkedActionAuditRollsBackWithItsOwnUserMutation() {
        rejectAuditAction("USER_ACTION_APPLIED");
        var request = new AdminRequests.UserActionRequest(AdminRequests.UserActionType.SUSPEND,
                "synthetic moderation reason", LocalDateTime.now().plusDays(1), reportId);
        assertThatThrownBy(() -> adminService.applyUserAction(adminId, subjectId, request)).isInstanceOf(RuntimeException.class);
        assertThat(users.findById(subjectId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(audits.count()).isZero();
        assertThat(storedReport().getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    void successfulLinkedActionRemainsWhenLaterReportSaveFailsAndAppearsInRealActionHistory() {
        LocalDateTime until = LocalDateTime.now().withNano(0).plusDays(1);
        adminService.applyUserAction(adminId, subjectId, new AdminRequests.UserActionRequest(
                AdminRequests.UserActionType.SUSPEND, "synthetic moderation reason", until, reportId));
        var linked = service.get(adminId, reportId).actions();
        assertThat(linked).hasSize(1);
        assertThat(linked.get(0).userId()).isEqualTo(subjectId);
        assertThat(linked.get(0).actorUserId()).isEqualTo(adminId);
        assertThat(linked.get(0).action()).isEqualTo("SUSPEND");
        assertThat(linked.get(0).until()).isEqualTo(until);
        var userFiltered = audits.search(adminId, AdminAuditAction.USER_ACTION_APPLIED,
                "USER", PageRequest.of(0, 20));
        assertThat(userFiltered.getTotalElements()).isEqualTo(1);
        assertThat(audits.count()).isEqualTo(1);
        AdminAuditLog actionAudit = userFiltered.getContent().get(0);
        assertThat(actionAudit.getTargetId()).isEqualTo(subjectId.toString());
        assertThat(actionAudit.getReportId()).isEqualTo(reportId);
        rejectAuditAction("REPORT_STATUS_UPDATED");
        assertThatThrownBy(() -> service.update(adminId, reportId, change(0, ReportStatus.RESOLVED, MEMO, REPLY)))
                .isInstanceOf(RuntimeException.class);
        assertThat(users.findById(subjectId).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(storedReport().getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(storedReport().getVersion()).isZero();
        assertThat(audits.countByAction(AdminAuditAction.USER_ACTION_APPLIED)).isEqualTo(1);
        assertThat(audits.countByAction(AdminAuditAction.REPORT_STATUS_UPDATED)).isZero();
        assertThat(service.get(adminId, reportId).actions()).isEqualTo(linked);
    }

    @Test
    void reportGetPatchAndEvidenceHttpEndpointsRejectAnonymousAndOrdinaryUsers() throws Exception {
        Long targetReport = createReport(subjectId, ReportSourceType.CHAT_MESSAGE, messageId.toString());
        var before = reportState();
        for (boolean authenticated : List.of(false, true)) {
            authenticateIfOrdinary(authenticated);
            mvc.perform(get(ENDPOINT + "/{id}", targetReport))
                    .andExpect(authenticated ? status().isForbidden() : status().isUnauthorized());
            authenticateIfOrdinary(authenticated);
            mvc.perform(patch(ENDPOINT + "/{id}", targetReport).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(change(0, ReportStatus.OPEN, MEMO, REPLY))))
                    .andExpect(authenticated ? status().isForbidden() : status().isUnauthorized());
            authenticateIfOrdinary(authenticated);
            mvc.perform(post(ENDPOINT + "/{id}/evidence-inspections", targetReport).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(inspection(roomId))))
                    .andExpect(authenticated ? status().isForbidden() : status().isUnauthorized())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
        assertError(() -> service.get(reporterId, targetReport), ErrorCode.FORBIDDEN);
        assertError(() -> service.get(null, targetReport), ErrorCode.FORBIDDEN);
        assertThat(reportState()).isEqualTo(before);
        assertThat(audits.count()).isZero();
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"RESTRICTED", "SUSPENDED", "DELETED"})
    void inactiveAdministratorCannotReadEditInspectOrApplyLinkedAction(UserStatus status) {
        inTransaction(() -> { ReflectionTestUtils.setField(users.findById(adminId).orElseThrow(), "status", status); return null; });
        assertError(() -> service.get(adminId, reportId), ErrorCode.FORBIDDEN);
        assertError(() -> service.update(adminId, reportId, change(0, ReportStatus.OPEN, MEMO, REPLY)), ErrorCode.FORBIDDEN);
        assertError(() -> service.inspectEvidence(adminId, reportId, inspection(roomId), "fixture"), ErrorCode.FORBIDDEN);
        assertError(() -> adminService.applyUserAction(adminId, subjectId,
                new AdminRequests.UserActionRequest(AdminRequests.UserActionType.SUSPEND, "fixture", null, reportId)), ErrorCode.FORBIDDEN);
        assertThat(audits.count()).isZero();
        verifyNoInteractions(notifications);
    }

    @Test
    void staleAdminPrincipalIsRejectedAfterCurrentDatabaseRoleWasDowngraded() throws Exception {
        inTransaction(() -> { users.findById(adminId).orElseThrow().changeRole(UserRole.USER); return null; });
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(ENDPOINT + "/{id}", reportId)).andExpect(status().isForbidden());
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(patch(ENDPOINT + "/{id}", reportId).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change(0, ReportStatus.OPEN, MEMO, REPLY))))
                .andExpect(status().isForbidden());
        assertThat(audits.count()).isZero();
    }

    private EvidenceInput evidenceInput(EvidenceCase fixture) {
        return switch (fixture) {
            case PROFILE -> evidence(ReportSourceType.PROFILE, subjectId, true, null, null, null);
            case WRONG_PROFILE -> evidence(ReportSourceType.PROFILE, outsiderId, false, null, null, null);
            case MISSING_PROFILE -> new EvidenceInput(Long.MAX_VALUE, ReportSourceType.PROFILE, Long.MAX_VALUE + "", false, null, null, null);
            case MESSAGE -> evidence(ReportSourceType.CHAT_MESSAGE, messageId, true, roomId, null, messageId);
            case WRONG_AUTHOR -> evidence(ReportSourceType.CHAT_MESSAGE, wrongAuthorMessageId, false, null, null, null);
            case OUTSIDE_MESSAGE -> evidence(ReportSourceType.CHAT_MESSAGE, otherMessageId, false, null, null, null);
            case MISSING_MESSAGE -> evidence(ReportSourceType.CHAT_MESSAGE, Long.MAX_VALUE, false, null, null, null);
            case ROOM -> evidence(ReportSourceType.CHAT_ROOM, roomId, true, roomId, null, null);
            case OUTSIDE_ROOM -> evidence(ReportSourceType.CHAT_ROOM, otherRoomId, false, null, null, null);
            case MISSING_SUBJECT_MEMBERSHIP -> { removeMembership(subjectId); yield evidence(ReportSourceType.CHAT_ROOM, roomId, false, null, null, null); }
            case MISSING_ROOM -> evidence(ReportSourceType.CHAT_ROOM, Long.MAX_VALUE, false, null, null, null);
            case MATCHING -> evidence(ReportSourceType.MATCHING_REQUEST, connectionId, true, roomId, connectionId, null);
            case OUTSIDE_MATCHING -> evidence(ReportSourceType.MATCHING_REQUEST, otherConnectionId, false, null, null, null);
            case MATCHING_WITHOUT_VALID_ROOM -> {
                inTransaction(() -> { connections.findById(connectionId).orElseThrow().accept(otherRoomId); return null; });
                yield evidence(ReportSourceType.MATCHING_REQUEST, connectionId, true, null, connectionId, null);
            }
            case MISSING_MATCHING -> evidence(ReportSourceType.MATCHING_REQUEST, Long.MAX_VALUE, false, null, null, null);
            case NULL_SOURCE -> new EvidenceInput(subjectId, ReportSourceType.CHAT_ROOM, null, false, null, null, null);
            case MALFORMED_SOURCE -> new EvidenceInput(subjectId, ReportSourceType.CHAT_ROOM, "https://outside.example.test/fixture", false, null, null, null);
            case ZERO_SOURCE -> evidence(ReportSourceType.CHAT_ROOM, 0L, false, null, null, null);
            case NEGATIVE_SOURCE -> evidence(ReportSourceType.CHAT_ROOM, -1L, false, null, null, null);
            case OVERFLOW_SOURCE -> new EvidenceInput(subjectId, ReportSourceType.CHAT_ROOM, "9223372036854775808", false, null, null, null);
            case OTHER_SOURCE -> evidence(ReportSourceType.OTHER, roomId, false, null, null, null);
        };
    }

    private EvidenceInput evidence(ReportSourceType type, Long source, boolean available, Long room, Long connection, Long message) {
        return new EvidenceInput(subjectId, type, source.toString(), available, room, connection, message);
    }

    private record EvidenceInput(Long subject, ReportSourceType type, String source, boolean available,
                                 Long room, Long connection, Long message) { }

    private boolean attemptChange(String label) {
        try {
            service.update(adminId, reportId, change(0, ReportStatus.IN_REVIEW, label, label + " reply"));
            return true;
        } catch (BusinessException failure) {
            assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REPORT_CONFLICT);
            return false;
        }
    }

    private AdminRequests.ReportStatusUpdateRequest change(long version, ReportStatus status, String memo, String reply) {
        return new AdminRequests.ReportStatusUpdateRequest(version, status, memo, reply);
    }

    private AdminRequests.ReportEvidenceInspectionRequest inspection(Long targetRoom) {
        return new AdminRequests.ReportEvidenceInspectionRequest(targetRoom, AdminRequests.ChatInspectionReason.REPORT_REVIEW,
                messageTime.minusMinutes(1), messageTime.plusMinutes(5), 0, 50);
    }

    private AdminDtos.ReportDetail updateHttp(Long targetReport, AdminRequests.ReportStatusUpdateRequest request) throws Exception {
        authenticate(adminId, UserRole.ADMIN);
        String json = mvc.perform(patch(ENDPOINT + "/{id}", targetReport).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.treeToValue(objectMapper.readTree(json).path("data"), AdminDtos.ReportDetail.class);
    }

    private void rejectAuditAction(String action) {
        jdbc.execute("ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_report_fixture_failure CHECK (action <> '" + action + "')");
    }

    private void assertNoPrivateAuditText(AdminAuditLog audit) {
        assertThat(audit.getMetadataJson()).doesNotContain(MEMO, REPLY);
        assertThat(audit.getSummary()).doesNotContain(MEMO, REPLY);
        assertThat(audit.getReason()).isNull();
    }

    private JsonNode auditMetadata(AdminAuditLog audit) throws Exception {
        JsonNode node = objectMapper.readTree(audit.getMetadataJson());
        return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
    }

    private UserReport storedReport() { return inTransaction(() -> reports.findById(reportId).orElseThrow()); }

    private LocalDateTime prepareTerminalFixture(ReportStatus terminal, boolean legacy) {
        LocalDateTime completed = LocalDateTime.of(2020, 1, 1, 1, 0);
        inTransaction(() -> {
            UserReport report = reports.findById(reportId).orElseThrow();
            report.updateHandling(terminal, "old internal note", REPLY, adminId);
            ReflectionTestUtils.setField(report, "createdAt", completed.minusHours(1));
            ReflectionTestUtils.setField(report, "updatedAt", completed);
            ReflectionTestUtils.setField(report, "completedAt", legacy ? null : completed);
            return null;
        });
        return completed;
    }

    private Long createReport(Long subject, ReportSourceType type, String source) {
        return inTransaction(() -> reports.saveAndFlush(report(subject, type, source)).getId());
    }

    private UserReport report(Long subject, ReportSourceType type, String source) {
        return UserReport.createReceived(reporterId, subject, ReportReasonCode.HARASSMENT,
                "synthetic reporter description", type, source);
    }

    private Long saveMessage(Long targetRoom, Long sender, String body, boolean deleted) {
        ChatMessage message = ChatMessage.create(targetRoom, sender, "fixture sender", body, MessageType.TEXT);
        ReflectionTestUtils.setField(message, "createdAt", messageTime);
        if (deleted) message.softDelete();
        return messages.saveAndFlush(message).getId();
    }

    private void removeMembership(Long targetUser) {
        inTransaction(() -> {
            members.delete(members.findByChatRoomIdAndUserId(roomId, targetUser).orElseThrow());
            return null;
        });
    }

    private Map<String, List<List<String>>> reportState() { return databaseState("user_reports"); }
    private Map<String, List<List<String>>> conversationState() { return databaseState("chat_rooms", "chat_messages", "chat_room_members"); }

    private Map<String, List<List<String>>> databaseState(String... tables) {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (String table : tables) {
            result.put(table, jdbc.query("SELECT * FROM " + table + " ORDER BY id", (row, number) -> {
                List<String> values = new ArrayList<>();
                for (int column = 1; column <= row.getMetaData().getColumnCount(); column++) values.add(row.getString(column));
                return values;
            }));
        }
        return result;
    }

    private User user(String nickname, UserRole role) {
        return User.builder().provider(SocialProvider.APPLE).socialId("report-fixture-" + UUID.randomUUID())
                .nickname(nickname).role(role).status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                .tickets(10).createdAt(LocalDateTime.now()).build();
    }

    private void authenticate(Long userId, UserRole role) {
        var principal = new CustomUserPrincipal(userId, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void authenticateIfOrdinary(boolean authenticated) {
        if (authenticated) authenticate(reporterId, UserRole.USER); else SecurityContextHolder.clearContext();
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
    }

    private <T> T inTransaction(Supplier<T> action) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction.execute(tx -> action.get());
    }

    private static <T> T awaitFuture(Future<T> future) {
        try { return future.get(WAIT_SECONDS, TimeUnit.SECONDS); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        catch (ExecutionException failure) { throw new AssertionError("Report worker failed", failure.getCause()); }
        catch (TimeoutException failure) { throw new AssertionError("Report worker exceeded bounded wait", failure); }
    }

    private static void stopExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try { assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    static class SqlProbe implements StatementInspector {
        final List<String> messageSelects = new CopyOnWriteArrayList<>();
        private volatile CyclicBarrier reportLockGate;
        private final AtomicInteger arrivals = new AtomicInteger();
        void armReportLockRace() { arrivals.set(0); reportLockGate = new CyclicBarrier(2); }
        int lockArrivals() { return arrivals.get(); }
        void clear() { reportLockGate = null; messageSelects.clear(); }

        @Override public String inspect(String sql) {
            String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
            if (normalized.startsWith("select ") && normalized.contains(" from chat_messages ")) messageSelects.add(normalized);
            CyclicBarrier gate = reportLockGate;
            if (gate != null && normalized.startsWith("select ") && normalized.contains(" from user_reports ")
                    && normalized.contains("for update") && arrivals.incrementAndGet() <= 2) {
                try { gate.await(WAIT_SECONDS, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                catch (Exception failure) { throw new AssertionError("Both real report lock queries must arrive", failure); }
            }
            return sql;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.admin", "univ.airconnect.user.repository",
            "univ.airconnect.moderation.repository", "univ.airconnect.chat.repository", "univ.airconnect.matching.repository",
            "univ.airconnect.notification.repository"})
    @Import({AdminReportService.class, AdminReportController.class, AdminService.class, AdminController.class, AdminAuditLogService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(
                "jdbc:h2:mem:admin-report-handling-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", ""); }
        @Bean SqlProbe sqlProbe() { return new SqlProbe(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, SqlProbe probe) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource); factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.session_factory.statement_inspector", probe));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
    }
}
