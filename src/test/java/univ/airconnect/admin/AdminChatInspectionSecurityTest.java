package univ.airconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletResponse;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionStatus;
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
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * Real AdminService, audit service, Spring transaction proxies and JPA against a dedicated
 * in-memory H2 database. No Boot application/configuration, environment files, production
 * resources, Redis, scheduler or external notification transport are loaded. HTTP tests start
 * at the already-authenticated principal boundary and apply the production ADMIN role rule;
 * they do not claim to test JWT cryptography or the complete production filter chain.
 *
 * Fixtures are committed outside the tested calls: REQUIRES_NEW audit commits and failures
 * would be obscured by wrapping this test class in a rollback-only test transaction.
 */
@SpringJUnitConfig(AdminChatInspectionSecurityTest.IsolatedJpaConfig.class)
class AdminChatInspectionSecurityTest {
    private static final String ORIGINAL_BODY = "inspection-only original body <script>fixture</script>";
    private static final String DELETED_BODY = "inspection-only deleted original body";
    private static final String OUTSIDE_ROOM_BODY = "other-room content must never appear";
    private static final String TRACE_ID = "inspection-fixture-trace";

    @Autowired AdminService adminService;
    @Autowired AdminAuditLogService auditService;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository members;
    @Autowired ChatMessageRepository messages;
    @Autowired ObjectMapper objectMapper;
    @Autowired LocalValidatorFactoryBean validator;
    @Autowired FailingCommitTransactionManager transactionManager;
    @MockitoSpyBean AdminAuditLogRepository audits;

    @MockitoBean UserReportRepository reports;
    @MockitoBean MatchingConnectionRepository matchingConnections;
    @MockitoBean IapOrderRepository orders;
    @MockitoBean TicketLedgerRepository ticketLedger;
    @MockitoBean AnalyticsEventRepository analytics;
    @MockitoBean UserService userService;
    @MockitoBean NotificationService notifications;
    @MockitoBean StatisticsService statistics;

    private TransactionTemplate transaction;
    private MockMvc mvc;
    private Long adminId;
    private Long senderId;
    private Long recipientId;
    private Long roomId;
    private Long otherRoomId;
    private Long originalMessageId;
    private Long deletedMessageId;
    private LocalDateTime fixtureTime;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        transactionManager.clearFailure();
        transaction = new TransactionTemplate(transactionManager);
        fixtureTime = now().withNano(0).minusHours(1);
        transaction.executeWithoutResult(tx -> {
            User admin = users.save(user("inspector", UserRole.ADMIN, UserStatus.ACTIVE));
            User sender = users.save(user("sender", UserRole.USER, UserStatus.ACTIVE));
            User recipient = users.save(user("recipient", UserRole.USER, UserStatus.ACTIVE));
            adminId = admin.getId();
            senderId = sender.getId();
            recipientId = recipient.getId();
            ChatRoom room = rooms.save(ChatRoom.createPersonal("Inspection fixture", senderId, recipientId, 7101L));
            roomId = room.getId();
            otherRoomId = rooms.save(ChatRoom.create("Different fixture room", ChatRoomType.GROUP)).getId();

            ChatMessage original = saveMessage(roomId, ORIGINAL_BODY, fixtureTime, false);
            originalMessageId = original.getId();
            ChatMessage deleted = saveMessage(roomId, DELETED_BODY, fixtureTime.plusMinutes(1), true);
            deletedMessageId = deleted.getId();
            saveMessage(otherRoomId, OUTSIDE_ROOM_BODY, fixtureTime.plusMinutes(2), false);
            room.updateLastMessage(DELETED_BODY, deleted.getCreatedAt());

            ChatRoomMember visible = ChatRoomMember.create(room, sender, originalMessageId);
            ReflectionTestUtils.setField(visible, "joinedAt", fixtureTime.minusDays(1));
            members.save(visible);
            ChatRoomMember hidden = ChatRoomMember.create(room, recipient);
            ReflectionTestUtils.setField(hidden, "joinedAt", fixtureTime.minusDays(1));
            hidden.hide("fixture-hidden-reason");
            members.save(hidden);
            // The inspector is deliberately not a room member.
        });

        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("inspection-fixture-anonymous"),
                translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        var controller = new AdminController(adminService, mock(AdminOperationsService.class),
                auditService, mock(AdminUserPurgeService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .addFilters(security)
                .build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        transactionManager.clearFailure();
        reset(audits);
        transaction.executeWithoutResult(tx -> {
            audits.deleteAllInBatch();
            members.deleteAllInBatch();
            messages.deleteAllInBatch();
            rooms.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test
    void metadataOnlyGetEndpoints_doNotExposeBodiesOrAcceptLegacyMessagePageBypass() throws Exception {
        authenticate(adminId, UserRole.ADMIN);
        ConversationState before = conversationState();
        var listResponse = mvc.perform(get("/api/v1/admin/chat-rooms")
                        .param("keyword", String.valueOf(roomId)))
                .andExpect(status().isOk()).andReturn().getResponse();
        // FilterChainProxy clears the thread SecurityContext after each request.
        authenticate(adminId, UserRole.ADMIN);
        var detailResponse = mvc.perform(get("/api/v1/admin/chat-rooms/{roomId}", roomId)
                        .param("messagePage", "0").param("messageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andReturn().getResponse();

        for (var response : List.of(listResponse, detailResponse)) {
            String json = response.getContentAsString();
            assertThat(json).doesNotContain(ORIGINAL_BODY, DELETED_BODY, OUTSIDE_ROOM_BODY,
                    "sender@example.test", "fixture-hidden-reason");
            JsonNode tree = objectMapper.readTree(json);
            for (String forbidden : List.of("content", "lastMessage", "messages", "email",
                    "lastReadMessageId", "hiddenAt", "hiddenReason")) {
                assertThat(tree.findValues(forbidden)).as("metadata must not include %s", forbidden).isEmpty();
            }
        }
        assertThat(audits.countByAction(AdminAuditAction.CHAT_MESSAGES_INSPECTED)).isZero();
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test
    void explicitInspection_preservesDeletedOriginalAndFlag_andCommitsExactBodyFreeAudit() throws Exception {
        authenticate(adminId, UserRole.ADMIN);
        var response = mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .requestAttr(TRACE_ID_ATTRIBUTE, TRACE_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(windowRequest(0, 50))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.messages.items[0].content").value(DELETED_BODY))
                .andExpect(jsonPath("$.data.messages.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.messages.items[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.messages.items[1].content").value(ORIGINAL_BODY))
                .andExpect(jsonPath("$.data.messages.items[1].deleted").value(false))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(OUTSIDE_ROOM_BODY);
        // This separate repository transaction observes the audit after the HTTP response.
        AdminAuditLog log = onlyInspectionLog();
        assertThat(log.getActorUserId()).isEqualTo(adminId);
        assertThat(log.getTargetType()).isEqualTo("CHAT_ROOM");
        assertThat(log.getTargetId()).isEqualTo(String.valueOf(roomId));
        assertThat(log.getReason()).isEqualTo("USER_SUPPORT");
        assertThat(log.getMetadataJson()).doesNotContain(ORIGINAL_BODY, DELETED_BODY,
                OUTSIDE_ROOM_BODY, "sender@example.test", "recipient@example.test");
        JsonNode metadata = auditMetadata(log);
        List<Long> auditedMessageIds = new ArrayList<>();
        metadata.path("returnedMessageIds").forEach(node -> auditedMessageIds.add(node.asLong()));
        assertThat(auditedMessageIds).containsExactly(deletedMessageId, originalMessageId);
        assertThat(metadata.path("returnedCount").asInt()).isEqualTo(2);
        assertThat(metadata.path("deletedCount").asInt()).isEqualTo(1);
        assertThat(metadata.path("page").asInt()).isZero();
        assertThat(metadata.path("size").asInt()).isEqualTo(50);
        assertThat(metadata.path("traceId").asText()).isEqualTo(TRACE_ID);
    }

    @Test
    void defaultWindowIsLast24HoursWithPageZeroAndFiftyMessages() throws Exception {
        transaction.executeWithoutResult(tx -> {
            saveMessage(roomId, "too old for default window", now().minusHours(25), false);
            for (int i = 0; i < 55; i++) {
                saveMessage(roomId, "default-window-" + i, fixtureTime.plusMinutes(3), false);
            }
        });
        LocalDateTime before = now();
        var result = inspect(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.USER_SUPPORT, null, null, null, null));
        LocalDateTime after = now();
        assertThat(result.to()).isBetween(before, after);
        assertThat(Duration.between(result.from(), result.to())).isEqualTo(Duration.ofHours(24));
        assertThat(result.messages().page()).isZero();
        assertThat(result.messages().size()).isEqualTo(50);
        assertThat(result.messages().items()).hasSize(50)
                .noneMatch(message -> message.content().equals("too old for default window"));
        assertThat(result.messages().totalElements()).isEqualTo(57);
        assertThat(result.messages().hasNext()).isTrue();
        JsonNode metadata = auditMetadata(onlyInspectionLog());
        assertThat(metadata.path("page").asInt()).isZero();
        assertThat(metadata.path("size").asInt()).isEqualTo(50);
        assertThat(metadata.hasNonNull("from")).isTrue();
        assertThat(metadata.hasNonNull("to")).isTrue();
    }

    @Test
    void reasonAndRangeAreMandatoryAndBoundedBeforeAnyInspectionAudit() throws Exception {
        assertInvalid(null);
        assertInvalid(new AdminRequests.ChatMessageInspectionRequest(null, null, null, null, null));
        for (int invalidSize : List.of(-1, 0, 101)) {
            assertInvalid(new AdminRequests.ChatMessageInspectionRequest(
                    AdminRequests.ChatInspectionReason.USER_SUPPORT, null, null, 0, invalidSize));
        }
        assertInvalid(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.USER_SUPPORT, null, null, -1, 50));
        assertInvalid(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.USER_SUPPORT,
                fixtureTime.minusDays(7).minusSeconds(1), fixtureTime, 0, 50));
        assertInvalid(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.USER_SUPPORT,
                fixtureTime.plusSeconds(1), fixtureTime, 0, 50));
        assertInvalid(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.USER_SUPPORT,
                fixtureTime, now().plusMinutes(5), 0, 50));
        assertThat(audits.count()).isZero();

        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(audits.count()).isZero();
    }

    @Test
    void sevenDayWindowAndSizeOneHundredAreAllowed_butNeverReturnMoreThanOneHundred() {
        transaction.executeWithoutResult(tx -> {
            for (int i = 0; i < 103; i++) {
                saveMessage(roomId, "bounded-page-" + i, fixtureTime.plusMinutes(3), false);
            }
        });
        LocalDateTime to = fixtureTime.plusHours(1);
        var result = inspect(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.DELIVERY_INCIDENT, to.minusDays(7), to, 0, 100));
        assertThat(result.messages().size()).isEqualTo(100);
        assertThat(result.messages().items()).hasSize(100);
        assertThat(result.messages().totalElements()).isEqualTo(105);
        assertThat(result.messages().hasNext()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"RESTRICTED", "SUSPENDED", "DELETED"})
    void inspectionRejectsAnAdministratorWhoseCurrentDatabaseStatusIsNotActive(UserStatus userStatus) {
        transaction.executeWithoutResult(tx -> ReflectionTestUtils.setField(
                users.findById(adminId).orElseThrow(), "status", userStatus));
        assertThatThrownBy(() -> inspect(windowRequest(0, 50)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(audits.count()).isZero();
    }

    @Test
    void serviceRejectsOrdinaryUserOrDowngradedAdmin_andHttpRejectsAnonymousAndNonAdmin() throws Exception {
        assertThatThrownBy(() -> adminService.inspectChatMessages(null, roomId,
                windowRequest(0, 50), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> adminService.inspectChatMessages(senderId, roomId,
                windowRequest(0, 50), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .contentType("application/json").content("{\"reason\":\"USER_SUPPORT\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").isEmpty());
        authenticate(senderId, UserRole.USER);
        mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .contentType("application/json").content("{\"reason\":\"USER_SUPPORT\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());

        // A stale authenticated ADMIN principal cannot override the current DB role.
        authenticate(adminId, UserRole.ADMIN);
        transaction.executeWithoutResult(tx -> users.findById(adminId).orElseThrow().changeRole(UserRole.USER));
        mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .contentType("application/json").content("{\"reason\":\"USER_SUPPORT\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(audits.count()).isZero();
    }

    @Test
    void roomTimeAndPageFiltersAreExact_stableOnEqualTimestamps_andEveryPageIsAudited() throws Exception {
        LocalDateTime boundary = fixtureTime.plusMinutes(10);
        List<Long> expected = transaction.execute(tx -> {
            saveMessage(roomId, "before-range", boundary.minusSeconds(1), false);
            Long first = saveMessage(roomId, "at-boundary-one", boundary, false).getId();
            Long second = saveMessage(roomId, "at-boundary-two", boundary, true).getId();
            Long third = saveMessage(roomId, "at-boundary-three", boundary, false).getId();
            saveMessage(roomId, "after-range", boundary.plusSeconds(1), false);
            saveMessage(otherRoomId, OUTSIDE_ROOM_BODY, boundary, false);
            return List.of(third, second, first);
        });
        var first = inspect(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.REPORT_REVIEW, boundary, boundary, 0, 2));
        var second = inspect(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.REPORT_REVIEW, boundary, boundary, 1, 2));
        var empty = inspect(new AdminRequests.ChatMessageInspectionRequest(
                AdminRequests.ChatInspectionReason.REPORT_REVIEW, boundary, boundary, 2, 2));
        assertThat(first.messages().items()).extracting(AdminDtos.ChatMessageItem::messageId)
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(second.messages().items()).extracting(AdminDtos.ChatMessageItem::messageId)
                .containsExactly(expected.get(2));
        assertThat(first.messages().items()).allMatch(message -> message.roomId().equals(roomId));
        assertThat(first.messages().totalElements()).isEqualTo(3);
        assertThat(first.messages().totalPages()).isEqualTo(2);
        assertThat(empty.messages().items()).isEmpty();
        List<AdminAuditLog> logs = audits.findAll(Sort.by("id"));
        assertThat(logs).hasSize(3);
        for (int page = 0; page < logs.size(); page++) {
            JsonNode metadata = auditMetadata(logs.get(page));
            assertThat(logs.get(page).getTargetId()).isEqualTo(roomId.toString());
            assertThat(metadata.path("page").asInt()).isEqualTo(page);
            assertThat(metadata.path("returnedCount").asInt()).isEqualTo(page == 0 ? 2 : page == 1 ? 1 : 0);
        }
    }

    @Test
    void missingRoomFailsBeforeAnyInspectionAudit() {
        assertThatThrownBy(() -> adminService.inspectChatMessages(adminId, Long.MAX_VALUE,
                windowRequest(0, 50), TRACE_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(audits.count()).isZero();
    }

    @Test
    void inspectionDoesNotMarkReadJoinUnhideModifyMessagesOrPublishNotifications() {
        ConversationState before = conversationState();
        inspect(windowRequest(0, 50));
        adminService.getChatRoomDetail(roomId);
        adminService.getChatRooms(0, 20, null, senderId, null);
        assertThat(conversationState()).isEqualTo(before);
        assertThat(members.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, adminId)).isFalse();
        assertThat(audits.countByAction(AdminAuditAction.CHAT_MESSAGES_INSPECTED)).isEqualTo(1);
        verifyNoInteractions(userService, notifications, statistics);
    }

    @Test
    void auditSaveFailureFailsClosedWithoutHttpBodyOrConversationChanges() throws Exception {
        authenticate(adminId, UserRole.ADMIN);
        ConversationState before = conversationState();
        doThrow(new DataIntegrityViolationException("isolated audit save failure"))
                .when(audits).saveAndFlush(any(AdminAuditLog.class));
        assertClosedFailure(postInspection(windowRequest(0, 50)));
        verify(audits).saveAndFlush(any(AdminAuditLog.class));
        assertThat(audits.count()).isZero();
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test
    void auditCommitFailureAfterSuccessfulFlushFailsClosed_andNextSuccessfulRequestCommits() throws Exception {
        authenticate(adminId, UserRole.ADMIN);
        ConversationState before = conversationState();
        transactionManager.failNextWriteCommit();
        assertClosedFailure(postInspection(windowRequest(0, 50)));
        // The real repository flush ran; failure occurred at the REQUIRES_NEW commit boundary.
        verify(audits).saveAndFlush(any(AdminAuditLog.class));
        assertThat(transactionManager.failedCommitAttempts()).isEqualTo(1);
        assertThat(audits.count()).isZero();
        assertThat(conversationState()).isEqualTo(before);

        authenticate(adminId, UserRole.ADMIN);
        MockHttpServletResponse success = postInspection(windowRequest(0, 50));
        assertThat(success.getStatus()).isEqualTo(200);
        assertThat(audits.countByAction(AdminAuditAction.CHAT_MESSAGES_INSPECTED)).isEqualTo(1);
    }

    @Test
    void retentionDeletesOnlyInspectionLogsStrictlyOlderThanNinetyDays() {
        LocalDateTime cutoff = now().withNano(0).minusDays(90);
        List<Long> ids = transaction.execute(tx -> List.of(
                saveAudit(AdminAuditAction.CHAT_MESSAGES_INSPECTED, cutoff.minusSeconds(1)).getId(),
                saveAudit(AdminAuditAction.CHAT_MESSAGES_INSPECTED, cutoff).getId(),
                saveAudit(AdminAuditAction.CHAT_MESSAGES_INSPECTED, cutoff.plusSeconds(1)).getId(),
                saveAudit(AdminAuditAction.TICKET_ADJUSTED, cutoff.minusDays(1)).getId()));
        ConversationState before = conversationState();
        assertThat(auditService.deleteExpiredChatInspectionLogs(cutoff)).isEqualTo(1);
        assertThat(audits.findAll()).extracting(AdminAuditLog::getId)
                .containsExactlyInAnyOrderElementsOf(ids.subList(1, 4));
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test
    void retentionWorkerUsesAuditLogLocalClockForNinetyDayCutoff_withoutStartingScheduling() {
        AdminAuditLogService isolatedProbe = mock(AdminAuditLogService.class);
        LocalDateTime before = LocalDateTime.now().minusDays(90);
        new AdminChatInspectionRetentionWorker(isolatedProbe).deleteExpiredLogs();
        LocalDateTime after = LocalDateTime.now().minusDays(90);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(isolatedProbe).deleteExpiredChatInspectionLogs(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    private AdminDtos.ChatMessageInspection inspect(AdminRequests.ChatMessageInspectionRequest request) {
        return adminService.inspectChatMessages(adminId, roomId, request, TRACE_ID);
    }

    private AdminRequests.ChatMessageInspectionRequest windowRequest(int page, int size) {
        return new AdminRequests.ChatMessageInspectionRequest(AdminRequests.ChatInspectionReason.USER_SUPPORT,
                fixtureTime.minusMinutes(1), fixtureTime.plusMinutes(30), page, size);
    }

    private void assertInvalid(AdminRequests.ChatMessageInspectionRequest request) {
        assertThatThrownBy(() -> inspect(request)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private MockHttpServletResponse postInspection(AdminRequests.ChatMessageInspectionRequest request) throws Exception {
        return mvc.perform(post("/api/v1/admin/chat-rooms/{roomId}/message-inspections", roomId)
                        .requestAttr(TRACE_ID_ATTRIBUTE, TRACE_ID)
                        .contentType("application/json").content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse();
    }

    private void assertClosedFailure(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(500);
        String json = response.getContentAsString();
        assertThat(json).doesNotContain(ORIGINAL_BODY, DELETED_BODY, OUTSIDE_ROOM_BODY);
        JsonNode tree = objectMapper.readTree(json);
        assertThat(tree.path("success").asBoolean()).isFalse();
        assertThat(tree.path("data").isMissingNode() || tree.path("data").isNull()).isTrue();
    }

    private void authenticate(Long userId, UserRole role) {
        CustomUserPrincipal principal = new CustomUserPrincipal(userId, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private AdminAuditLog onlyInspectionLog() {
        List<AdminAuditLog> found = audits.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.CHAT_MESSAGES_INSPECTED).toList();
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private JsonNode auditMetadata(AdminAuditLog log) throws Exception {
        JsonNode node = objectMapper.readTree(log.getMetadataJson());
        // H2's JSON column can return a JDBC String as a JSON string literal. Decode that
        // representation once; assertions still cover the persisted metadata, not a mock.
        return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
    }

    private ChatMessage saveMessage(Long targetRoomId, String content, LocalDateTime createdAt, boolean deleted) {
        ChatMessage message = ChatMessage.create(targetRoomId, senderId, "sender", content, MessageType.TEXT);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        if (deleted) {
            message.softDelete();
            ReflectionTestUtils.setField(message, "deletedAt", createdAt.plusSeconds(10));
        }
        return messages.save(message);
    }

    private AdminAuditLog saveAudit(AdminAuditAction action, LocalDateTime createdAt) {
        AdminAuditLog log = AdminAuditLog.create(adminId, action, "CHAT_ROOM", roomId.toString(),
                "retention fixture", "USER_SUPPORT", "{}");
        ReflectionTestUtils.setField(log, "createdAt", createdAt);
        return audits.save(log);
    }

    private User user(String nickname, UserRole role, UserStatus status) {
        return User.builder().provider(SocialProvider.KAKAO).socialId(UUID.randomUUID().toString())
                .email(nickname + "@example.test").nickname(nickname).role(role).status(status)
                .onboardingStatus(OnboardingStatus.FULL).createdAt(now()).tickets(10).build();
    }

    private ConversationState conversationState() {
        return transaction.execute(tx -> new ConversationState(
                messages.findAll(Sort.by("id")).stream().map(message -> new MessageState(
                        message.getId(), message.getRoomId(), message.getSenderId(), message.getContent(),
                        message.getLegacyMessage(), message.getType(), message.getCreatedAt(),
                        message.isDeleted(), message.getDeletedAt(), message.getReadAt())).toList(),
                members.findAll(Sort.by("id")).stream().map(member -> new MemberState(
                        member.getId(), member.getChatRoom().getId(), member.getUser().getId(),
                        member.getJoinedAt(), member.getLastReadMessageId(), member.getHiddenAt(),
                        member.getHiddenReason())).toList(),
                rooms.findAll(Sort.by("id")).stream().map(room -> new RoomState(
                        room.getId(), room.getLastMessage(), room.getLastMessageAt(), room.getUpdatedAt())).toList()));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    private record ConversationState(List<MessageState> messages, List<MemberState> members, List<RoomState> rooms) { }
    private record MessageState(Long id, Long roomId, Long senderId, String content, String legacyContent,
                                MessageType type, LocalDateTime createdAt, boolean deleted,
                                LocalDateTime deletedAt, LocalDateTime readAt) { }
    private record MemberState(Long id, Long roomId, Long userId, LocalDateTime joinedAt,
                               Long lastReadMessageId, LocalDateTime hiddenAt, String hiddenReason) { }
    private record RoomState(Long id, String lastMessage, LocalDateTime lastMessageAt, LocalDateTime updatedAt) { }

    /** Fails only an explicitly armed non-read-only commit, after a real JPA flush. */
    static class FailingCommitTransactionManager extends JpaTransactionManager {
        private final AtomicBoolean failWriteCommit = new AtomicBoolean();
        private final AtomicInteger failedCommits = new AtomicInteger();

        FailingCommitTransactionManager(EntityManagerFactory entityManagerFactory) {
            super(entityManagerFactory);
        }

        void failNextWriteCommit() {
            failWriteCommit.set(true);
        }

        void clearFailure() {
            failWriteCommit.set(false);
            failedCommits.set(0);
        }

        int failedCommitAttempts() {
            return failedCommits.get();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (!status.isReadOnly() && failWriteCommit.compareAndSet(true, false)) {
                failedCommits.incrementAndGet();
                // Roll back the real inner audit transaction so the test does not depend on
                // provider-specific EntityManager.close handling after a simulated commit error.
                super.doRollback(status);
                throw new TransactionSystemException("isolated audit commit failure after flush");
            }
            super.doCommit(status);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {
            "univ.airconnect.admin", "univ.airconnect.chat.repository", "univ.airconnect.user.repository"
    })
    @Import({AdminService.class, AdminAuditLogService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:admin-chat-inspection-security;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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

        @Bean FailingCommitTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new FailingCommitTransactionManager(entityManagerFactory);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
