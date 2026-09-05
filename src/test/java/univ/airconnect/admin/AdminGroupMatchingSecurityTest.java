package univ.airconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.RedisTemplate;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
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
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.groupmatching.service.GMatchingQueueWorker;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.PushEventType;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real diagnostic service, repositories, transaction proxies and HTTP controller against
 * dedicated in-memory H2. No Boot application, property/resource loading, Redis connection,
 * matching worker or mutating matching service is started. Authentication begins at a
 * synthetic principal and uses the same ADMIN authorization rule as production.
 * Fixtures commit separately; observations are checked against persisted before/after state.
 */
@SpringJUnitConfig(AdminGroupMatchingSecurityTest.IsolatedJpaConfig.class)
class AdminGroupMatchingSecurityTest {
    private static final String PATH = "/api/v1/admin/group-matching/teams";
    private static final String BODY = "synthetic group message body must not be returned";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "content", "message", "messages", "lastMessage", "inviteCode", "queueToken");

    @Autowired AdminGroupMatchingService service;
    @Autowired GTemporaryTeamRoomRepository teams;
    @Autowired GTemporaryTeamMemberRepository participants;
    @Autowired GTeamReadyStateRepository readiness;
    @Autowired GMatchResultRepository results;
    @Autowired GFinalGroupChatRoomRepository finalRooms;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository chatMembers;
    @Autowired ChatMessageRepository messages;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext context;
    @PersistenceContext EntityManager entityManager;
    @MockitoBean AdminGroupQueueObserver queueObserver;
    @MockitoSpyBean AdminGroupMatchingRepository diagnostics;

    private TransactionTemplate transaction;
    private MockMvc mvc;
    private Long adminId;
    private Long ordinaryId;
    private final Map<Long, String> queuePresence = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        transaction = new TransactionTemplate(transactionManager);
        queuePresence.clear();
        transaction.executeWithoutResult(tx -> {
            adminId = users.save(user("diagnostic-admin", UserRole.ADMIN)).getId();
            ordinaryId = users.save(user("diagnostic-user", UserRole.USER)).getId();
            seedNotificationRows();
        });
        when(queueObserver.observe(any(GTeamSize.class), anyLong())).thenAnswer(call -> {
            return queueObservation(call.getArgument(1));
        });
        when(queueObserver.observeMany(any(GTeamSize.class), anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(1);
            Map<Long, AdminGroupMatchingDtos.Queue> observations = new LinkedHashMap<>();
            ids.forEach(id -> observations.put(id, queueObservation(id)));
            return observations;
        });
        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("group-diagnostic-fixture"), translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(new AdminGroupMatchingController(service))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(security).build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        transaction.executeWithoutResult(tx -> {
            entityManager.createQuery("delete from PushEvent").executeUpdate();
            entityManager.createQuery("delete from NotificationOutbox").executeUpdate();
            entityManager.createQuery("delete from Notification").executeUpdate();
            messages.deleteAllInBatch();
            chatMembers.deleteAllInBatch();
            finalRooms.deleteAllInBatch();
            results.deleteAllInBatch();
            readiness.deleteAllInBatch();
            participants.deleteAllInBatch();
            teams.deleteAllInBatch();
            rooms.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    void stagesAndCompletedTwoByTwoOrThreeByThreeAreDiagnosticOnly(GTeamSize size) {
        var recruiting = fixture(size, GTemporaryTeamRoomStatus.OPEN, 0);
        var checking = fixture(size, GTemporaryTeamRoomStatus.READY_CHECK, size.getValue() - 1);
        var ready = fixture(size, GTemporaryTeamRoomStatus.READY_CHECK, size.getValue());
        var waiting = fixture(size, GTemporaryTeamRoomStatus.QUEUE_WAITING, size.getValue());
        var pending = matchedPair(size, false);
        var completed = matchedPair(size, true);
        var cancelled = fixture(size, GTemporaryTeamRoomStatus.CANCELLED, 0);
        var before = databaseState();
        assertThat(before.get("notifications")).hasSize(1);
        assertThat(before.get("notification_outbox")).hasSize(1);
        assertThat(before.get("push_events")).hasSize(1);

        assertThat(detail(recruiting).stage()).isEqualTo("RECRUITING");
        assertThat(detail(checking).stage()).isEqualTo("CHECKING_READY");
        assertThat(detail(ready).stage()).isEqualTo("WAITING_FOR_START");
        assertThat(detail(waiting).stage()).isEqualTo("WAITING");
        var pendingDetail = detail(pending.first());
        assertThat(pendingDetail.stage()).isEqualTo("FINAL_ROOM_PENDING");
        assertThat(pendingDetail.matches()).hasSize(1);
        assertThat(pendingDetail.finalRooms()).isEmpty();
        assertThat(detail(cancelled).stage()).isEqualTo("CANCELLED");

        var finished = detail(completed.first());
        assertThat(finished.stage()).isEqualTo("FINAL_ROOM_CREATED");
        assertThat(finished.team().teamSize()).isEqualTo(size.getValue());
        assertThat(finished.team().storedMemberCount()).isEqualTo(size.getValue());
        assertThat(finished.activeMemberCount()).isZero();
        assertThat(finished.readyMemberCount()).isZero();
        assertThat(finished.participants()).hasSize(size.getValue()).allMatch(p -> !p.active() && p.ready() == null);
        assertThat(finished.temporaryRoom().membershipCount()).isZero();
        assertThat(finished.finalRooms()).singleElement().satisfies(room -> {
            assertThat(room.chatRoom().exists()).isTrue();
            assertThat(room.chatRoom().group()).isTrue();
            assertThat(room.chatRoom().membershipCount()).isEqualTo(size.getValue() * 2L);
        });
        assertThat(finished.observations()).isEmpty();
        service.list(adminId, null, null, null, 0, 50);
        assertThat(databaseState()).isEqualTo(before);
        assertThat(context.getBeansOfType(GMatchingService.class)).isEmpty();
        assertThat(context.getBeansOfType(GMatchingQueueWorker.class)).isEmpty();
        assertThat(context.getBeansOfType(RedisTemplate.class)).isEmpty();
    }

    @Test
    void missingQueueMetadataOrRedisEntryIsObservedWithoutRecovery() {
        var waiting = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.QUEUE_WAITING, 2);
        transaction.executeWithoutResult(tx -> {
            var team = teams.findById(waiting.teamId()).orElseThrow();
            ReflectionTestUtils.setField(team, "queuedAt", null);
            ReflectionTestUtils.setField(team, "queueToken", null);
        });
        queuePresence.remove(waiting.teamId());
        var before = databaseState();

        var result = detail(waiting);

        assertThat(result.stage()).isEqualTo("WAITING");
        assertThat(result.queue().presence()).isEqualTo("NOT_OBSERVED");
        assertThat(result.observations()).anyMatch(text -> text.contains("DB 대기 시작 시각"))
                .anyMatch(text -> text.contains("관찰하지 못했습니다"));
        assertThat(databaseState()).isEqualTo(before);
    }

    enum LinkFault { MISSING_FINAL, WRONG_RESULT, WRONG_TEAM, WRONG_SIZE, MISSING_CHAT, PERSONAL_CHAT, WRONG_FINAL_ID }

    @ParameterizedTest
    @EnumSource(LinkFault.class)
    void incompleteOrConflictingFinalLinksAreUnknownAndNeverRepaired(LinkFault fault) {
        var pair = matchedPair(GTeamSize.TWO, true);
        transaction.executeWithoutResult(tx -> {
            var finalRoom = finalRooms.findById(pair.finalRoomId()).orElseThrow();
            switch (fault) {
                case MISSING_FINAL -> finalRooms.delete(finalRoom);
                case WRONG_RESULT -> ReflectionTestUtils.setField(finalRoom, "matchResultId", Long.MAX_VALUE - 1);
                case WRONG_TEAM -> ReflectionTestUtils.setField(finalRoom, "team2RoomId", Long.MAX_VALUE - 2);
                case WRONG_SIZE -> ReflectionTestUtils.setField(finalRoom, "teamSize", GTeamSize.THREE);
                case MISSING_CHAT -> ReflectionTestUtils.setField(finalRoom, "chatRoomId", Long.MAX_VALUE - 3);
                case PERSONAL_CHAT -> ReflectionTestUtils.setField(finalRoom, "chatRoomId",
                        rooms.save(ChatRoom.create("personal-fixture", ChatRoomType.PERSONAL)).getId());
                case WRONG_FINAL_ID -> ReflectionTestUtils.setField(results.findById(pair.resultId()).orElseThrow(),
                        "finalGroupChatRoomId", Long.MAX_VALUE - 4);
            }
        });
        var before = databaseState();

        var result = detail(pair.first());

        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.observations()).anyMatch(text -> text.contains("연결이 누락되었거나 서로 다릅니다"));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void missingUsersReadinessAndTemporaryRoomAreReportedWithoutChangingRows() {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.READY_CHECK, 2);
        transaction.executeWithoutResult(tx -> {
            ReflectionTestUtils.setField(teams.findById(team.teamId()).orElseThrow(), "currentMemberCount", 4);
            ReflectionTestUtils.setField(teams.findById(team.teamId()).orElseThrow(), "tempChatRoomId", Long.MAX_VALUE);
            participants.save(GTemporaryTeamMember.create(team.teamId(), Long.MAX_VALUE - 1, false));
            readiness.findByTeamRoomIdAndUserId(team.teamId(), team.userIds().get(0)).ifPresent(readiness::delete);
            readiness.save(GTeamReadyState.create(team.teamId(), ordinaryId));
        });
        var before = databaseState();

        var result = detail(team);

        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.observations()).anyMatch(text -> text.contains("저장 인원"))
                .anyMatch(text -> text.contains("준비 기록 일부"))
                .anyMatch(text -> text.contains("현재 참여자가 아닌"))
                .anyMatch(text -> text.contains("회원 정보가 없어진"))
                .anyMatch(text -> text.contains("임시 채팅방 연결"));
        assertThat(result.participants()).anyMatch(p -> !p.userRecordPresent() && p.nickname() == null);
        assertThat(result.temporaryRoom().exists()).isFalse();
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void missingOpponentIsVisibleWithoutInventingAnOpponentRecord() {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.MATCHED, 2);
        transaction.executeWithoutResult(tx -> results.save(GMatchResult.create(team.teamId(), Long.MAX_VALUE)));
        var before = databaseState();

        var result = detail(team);

        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.opponentExists()).isFalse();
            assertThat(match.opponentTeamName()).isNull();
        });
        assertThat(result.observations()).anyMatch(text -> text.contains("상대 팀 기록"));
        assertThat(databaseState()).isEqualTo(before);
    }

    enum PendingFault { OPPONENT_CANCELLED, OPPONENT_WRONG_SIZE, OPPONENT_MEMBER_MISSING, OWN_MEMBER_MISSING }

    @ParameterizedTest
    @EnumSource(PendingFault.class)
    void aMatchRowAloneCannotClaimFinalRoomPendingWhenEitherTeamIsInconsistent(PendingFault fault) {
        var pair = matchedPair(GTeamSize.TWO, false);
        transaction.executeWithoutResult(tx -> {
            var opponent = teams.findById(pair.second().teamId()).orElseThrow();
            switch (fault) {
                case OPPONENT_CANCELLED -> opponent.cancel(opponent.getLeaderId());
                case OPPONENT_WRONG_SIZE -> ReflectionTestUtils.setField(opponent, "teamSize", GTeamSize.THREE);
                case OPPONENT_MEMBER_MISSING -> participants.findByTeamRoomIdAndUserId(
                        pair.second().teamId(), pair.second().userIds().get(1)).orElseThrow().markLeft();
                case OWN_MEMBER_MISSING -> participants.findByTeamRoomIdAndUserId(
                        pair.first().teamId(), pair.first().userIds().get(1)).orElseThrow().markLeft();
            }
        });
        var before = databaseState();

        var result = detail(pair.first());

        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.observations()).isNotEmpty();
        assertThat(result.matches()).hasSize(1);
        assertThat(result.finalRooms()).isEmpty();
        assertThat(databaseState()).isEqualTo(before);
    }

    enum ReadyFault { STORED_MEMBER_COUNT_MISMATCH, LEADER_NOT_ACTIVE }

    @ParameterizedTest
    @EnumSource(ReadyFault.class)
    void allReadyFlagsCannotClaimWaitingForStartWhenStoredCountOrActiveLeaderIsInvalid(ReadyFault fault) {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.READY_CHECK, 2);
        transaction.executeWithoutResult(tx -> {
            if (fault == ReadyFault.STORED_MEMBER_COUNT_MISMATCH) {
                ReflectionTestUtils.setField(teams.findById(team.teamId()).orElseThrow(), "currentMemberCount", 1);
            } else {
                Long leader = team.userIds().get(0);
                participants.findByTeamRoomIdAndUserId(team.teamId(), leader).orElseThrow().markLeft();
                readiness.findByTeamRoomIdAndUserId(team.teamId(), leader).ifPresent(readiness::delete);
                participants.save(GTemporaryTeamMember.create(team.teamId(), ordinaryId, false));
                var replacementReady = GTeamReadyState.create(team.teamId(), ordinaryId);
                replacementReady.markReady();
                readiness.save(replacementReady);
            }
        });
        var before = databaseState();

        var result = detail(team);

        assertThat(result.activeMemberCount()).isEqualTo(2);
        assertThat(result.readyMemberCount()).isEqualTo(2);
        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.observations()).isNotEmpty();
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void userSearchIncludesPastParticipationAndLeadershipAndAppliesPageTeamAndStatusFilters() throws Exception {
        var old = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.OPEN, 0);
        var cancelled = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.CANCELLED, 0);
        var led = transaction.execute(tx -> fixtureInTransaction(GTeamSize.THREE,
                GTemporaryTeamRoomStatus.OPEN, 0, ordinaryId));
        var unrelated = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.OPEN, 0);
        transaction.executeWithoutResult(tx -> {
            for (Long id : List.of(old.teamId(), cancelled.teamId())) {
                var former = GTemporaryTeamMember.create(id, ordinaryId, false);
                former.markLeft();
                participants.save(former);
            }
        });
        var before = databaseState();

        var first = service.list(adminId, ordinaryId, null, null, 0, 1);
        var second = service.list(adminId, ordinaryId, null, null, 1, 1);
        var third = service.list(adminId, ordinaryId, null, null, 2, 1);
        assertThat(first.items()).extracting(AdminGroupMatchingDtos.Team::teamId).containsExactly(led.teamId());
        assertThat(second.items()).extracting(AdminGroupMatchingDtos.Team::teamId).containsExactly(cancelled.teamId());
        assertThat(third.items()).extracting(AdminGroupMatchingDtos.Team::teamId).containsExactly(old.teamId());
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(third.hasNext()).isFalse();
        assertThat(service.list(adminId, ordinaryId, null, GTemporaryTeamRoomStatus.CANCELLED, 0, 10).items())
                .extracting(AdminGroupMatchingDtos.Team::teamId).containsExactly(cancelled.teamId());
        assertThat(service.list(adminId, ordinaryId, old.teamId(), null, 0, 10).items())
                .extracting(AdminGroupMatchingDtos.Team::teamId).containsExactly(old.teamId());
        assertThat(service.list(adminId, ordinaryId, unrelated.teamId(), null, 0, 10).items()).isEmpty();
        assertThat(service.list(adminId, ordinaryId, null, null, 3, 1).items()).isEmpty();

        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH).param("userId", ordinaryId.toString()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items[0].teamId").value(cancelled.teamId().intValue()));
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(queueObserver);
    }

    @Test
    void historyLimitIsExplicitAndPreventsAConfidentStageClaim() {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.MATCHED, 2);
        transaction.executeWithoutResult(tx -> {
            for (int i = 0; i < 21; i++) {
                var match = GMatchResult.create(team.teamId(), Long.MAX_VALUE - i);
                if (i < 20) match.cancel();
                results.save(match);
            }
        });
        var before = databaseState();

        var result = detail(team);

        assertThat(result.historyTruncated()).isTrue();
        assertThat(result.matches()).hasSize(20);
        assertThat(result.stage()).isEqualTo("UNKNOWN");
        assertThat(result.observations()).anyMatch(text -> text.contains("최근 20건"));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void anonymousOrdinaryAndStaleAdminPrincipalsCannotReadDiagnostics() throws Exception {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.OPEN, 0);
        var before = databaseState();
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        authenticate(ordinaryId, UserRole.USER);
        mvc.perform(get(PATH + "/{teamId}", team.teamId())).andExpect(status().isForbidden());
        assertForbidden(() -> service.detail(null, team.teamId()));
        assertForbidden(() -> service.detail(ordinaryId, team.teamId()));
        assertForbidden(() -> service.list(Long.MAX_VALUE, null, null, null, 0, 10));
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(queueObserver);

        transaction.executeWithoutResult(tx -> users.findById(adminId).orElseThrow().changeRole(UserRole.USER));
        before = databaseState();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH + "/{teamId}", team.teamId())).andExpect(status().isForbidden());
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(queueObserver);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "RESTRICTED", "DELETED"})
    void currentNonActiveAdminStatusIsRejectedAtTheServiceBoundary(UserStatus status) {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.OPEN, 0);
        transaction.executeWithoutResult(tx -> ReflectionTestUtils.setField(
                users.findById(adminId).orElseThrow(), "status", status));
        var before = databaseState();

        assertForbidden(() -> service.detail(adminId, team.teamId()));
        assertForbidden(() -> service.list(adminId, null, null, null, 0, 10));

        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(queueObserver);
    }

    @Test
    void successfulHttpResponsesNeverContainBodiesInvitationCodesOrQueueTokens() throws Exception {
        var team = fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.QUEUE_WAITING, 2);
        var before = databaseState();
        authenticate(adminId, UserRole.ADMIN);
        String detailJson = mvc.perform(get(PATH + "/{teamId}", team.teamId()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.stage").value("WAITING"))
                .andReturn().getResponse().getContentAsString();
        authenticate(adminId, UserRole.ADMIN);
        String listJson = mvc.perform(get(PATH).param("teamId", team.teamId().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();

        for (String json : List.of(detailJson, listJson)) {
            assertThat(json).doesNotContain(BODY, team.inviteCode(), team.queueToken());
            assertNoForbiddenFields(objectMapper.readTree(json));
        }
        assertThat(databaseState()).isEqualTo(before);
        assertThat(chatMembers.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(team.chatRoomId(), adminId)).isFalse();
    }

    @Test
    void invalidFiltersAndMissingTeamsFailWithoutQueueReads() throws Exception {
        assertInvalid(() -> service.list(adminId, null, null, null, -1, 10));
        assertInvalid(() -> service.list(adminId, null, null, null, 0, 0));
        assertInvalid(() -> service.list(adminId, null, null, null, 0, 51));
        assertInvalid(() -> service.list(adminId, 0L, null, null, 0, 10));
        assertInvalid(() -> service.list(adminId, null, -1L, null, 0, 10));
        assertInvalid(() -> service.detail(adminId, null));
        assertThatThrownBy(() -> service.detail(adminId, Long.MAX_VALUE))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH).param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(queueObserver);
    }

    @Test
    void waitingBoardSeparatesBothSizesAndGendersAndExcludesEveryNonWaitingStage() {
        LocalDateTime queuedAt = LocalDateTime.now().minusMinutes(10);
        var twoMale = waitingFixture(GTeamSize.TWO, GTeamGender.M, queuedAt);
        var twoFemale = waitingFixture(GTeamSize.TWO, GTeamGender.F, queuedAt);
        var threeMale = waitingFixture(GTeamSize.THREE, GTeamGender.M, queuedAt);
        var threeFemale = waitingFixture(GTeamSize.THREE, GTeamGender.F, queuedAt);
        fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.OPEN, 0);
        fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.READY_CHECK, 1);
        fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.READY_CHECK, 2);
        fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.CANCELLED, 0);
        matchedPair(GTeamSize.TWO, false);
        matchedPair(GTeamSize.TWO, true);
        var before = databaseState();

        var two = service.waiting(adminId, 2, null, 0, 0, 20);
        var three = service.waiting(adminId, 3, null, 0, 0, 20);

        assertWaitingIds(two.male(), twoMale.teamId());
        assertWaitingIds(two.female(), twoFemale.teamId());
        assertWaitingIds(three.male(), threeMale.teamId());
        assertWaitingIds(three.female(), threeFemale.teamId());
        for (var board : List.of(two, three)) {
            assertThat(board.male().items()).allSatisfy(row -> {
                assertThat(row.team().teamGender()).isEqualTo("M");
                assertThat(row.team().teamSize()).isEqualTo(board.teamSize());
                assertThat(row.team().status()).isEqualTo("QUEUE_WAITING");
            });
            assertThat(board.female().items()).allSatisfy(row -> {
                assertThat(row.team().teamGender()).isEqualTo("F");
                assertThat(row.team().teamSize()).isEqualTo(board.teamSize());
                assertThat(row.team().status()).isEqualTo("QUEUE_WAITING");
            });
            assertThat(board.male().totalElements()).isEqualTo(1);
            assertThat(board.female().totalElements()).isEqualTo(1);
        }
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingPagesAreIndependentWithOldestFirstIdTieBreakAndNullTimesLast() throws Exception {
        LocalDateTime oldest = LocalDateTime.now().minusHours(2);
        var maleLater = waitingFixture(GTeamSize.TWO, GTeamGender.M, oldest.plusMinutes(1));
        var maleTieFirst = waitingFixture(GTeamSize.TWO, GTeamGender.M, oldest);
        var maleNull = waitingFixture(GTeamSize.TWO, GTeamGender.M, null);
        var maleTieSecond = waitingFixture(GTeamSize.TWO, GTeamGender.M, oldest);
        var femaleNull = waitingFixture(GTeamSize.TWO, GTeamGender.F, null);
        var femaleOldest = waitingFixture(GTeamSize.TWO, GTeamGender.F, oldest);
        var femaleLater = waitingFixture(GTeamSize.TWO, GTeamGender.F, oldest.plusMinutes(1));
        var before = databaseState();

        var first = service.waiting(adminId, 2, null, 0, 1, 2);
        var second = service.waiting(adminId, 2, null, 1, 0, 2);
        var outside = service.waiting(adminId, 2, null, 5, 5, 2);

        assertWaitingIds(first.male(), maleTieFirst.teamId(), maleTieSecond.teamId());
        assertWaitingIds(first.female(), femaleNull.teamId());
        assertWaitingIds(second.male(), maleLater.teamId(), maleNull.teamId());
        assertWaitingIds(second.female(), femaleOldest.teamId(), femaleLater.teamId());
        assertThat(first.male().page()).isZero();
        assertThat(first.female().page()).isEqualTo(1);
        assertThat(first.male().totalElements()).isEqualTo(4);
        assertThat(first.female().totalElements()).isEqualTo(3);
        assertThat(first.male().totalPages()).isEqualTo(2);
        assertThat(first.female().totalPages()).isEqualTo(2);
        assertThat(first.male().hasNext()).isTrue();
        assertThat(first.female().hasNext()).isFalse();
        assertThat(second.male().hasNext()).isFalse();
        assertThat(second.female().hasNext()).isTrue();
        assertThat(outside.male().items()).isEmpty();
        assertThat(outside.female().items()).isEmpty();
        assertThat(outside.male().totalElements()).isEqualTo(4);
        assertThat(outside.female().totalElements()).isEqualTo(3);
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH + "/waiting").param("malePage", "1").param("femalePage", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.male.page").value(1))
                .andExpect(jsonPath("$.data.female.page").value(0))
                .andExpect(jsonPath("$.data.male.items[0].team.teamId").value(maleLater.teamId().intValue()))
                .andExpect(jsonPath("$.data.female.items[0].team.teamId").value(femaleOldest.teamId().intValue()));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingCountsAreBatchedAndDoNotHideTeamsWithMissingActiveMembers() {
        var male = waitingFixture(GTeamSize.TWO, GTeamGender.M, LocalDateTime.now().minusMinutes(1));
        var female = waitingFixture(GTeamSize.TWO, GTeamGender.F, LocalDateTime.now().minusMinutes(1));
        transaction.executeWithoutResult(tx -> {
            participants.findByTeamRoomIdAndUserId(male.teamId(), male.userIds().get(1)).orElseThrow().markLeft();
            participants.findByTeamRoomIdOrderByJoinedAtAsc(female.teamId()).forEach(GTemporaryTeamMember::markLeft);
        });
        var before = databaseState();

        var board = service.waiting(adminId, 2, null, 0, 0, 5);

        assertWaitingIds(board.male(), male.teamId());
        assertWaitingIds(board.female(), female.teamId());
        assertThat(board.male().items().get(0).activeMemberCount()).isEqualTo(1);
        assertThat(board.female().items().get(0).activeMemberCount()).isZero();
        assertThat(board.male().items().get(0).team().storedMemberCount()).isEqualTo(2);
        assertThat(board.female().items().get(0).team().storedMemberCount()).isEqualTo(2);
        verify(diagnostics).activeCounts(List.of(male.teamId(), female.teamId()));
        verify(diagnostics, times(1)).activeCounts(any());
        verify(queueObserver).observeMany(GTeamSize.TWO, List.of(male.teamId(), female.teamId()));
        verify(queueObserver, never()).observe(any(), anyLong());
        verifyNoMoreInteractions(queueObserver);
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingUserFilterIncludesLeaderOrActiveMemberButNotFormerOrExpelledMembers() throws Exception {
        LocalDateTime time = LocalDateTime.now().minusMinutes(2);
        var led = waitingFixture(GTeamSize.TWO, GTeamGender.M, time, ordinaryId);
        var active = waitingFixture(GTeamSize.TWO, GTeamGender.F, time);
        var former = waitingFixture(GTeamSize.TWO, GTeamGender.M, time);
        var expelled = waitingFixture(GTeamSize.TWO, GTeamGender.F, time);
        waitingFixture(GTeamSize.TWO, GTeamGender.F, time);
        transaction.executeWithoutResult(tx -> {
            // The contract includes the stored leader independently of membership state.
            participants.findByTeamRoomIdAndUserId(led.teamId(), ordinaryId).orElseThrow().markLeft();
            participants.save(GTemporaryTeamMember.create(active.teamId(), ordinaryId, false));
            var historical = GTemporaryTeamMember.create(former.teamId(), ordinaryId, false);
            historical.markLeft();
            participants.save(historical);
            var removed = GTemporaryTeamMember.create(expelled.teamId(), ordinaryId, false);
            removed.markExpelled();
            participants.save(removed);
        });
        var before = databaseState();

        var board = service.waiting(adminId, 2, ordinaryId, 0, 0, 20);

        assertWaitingIds(board.male(), led.teamId());
        assertWaitingIds(board.female(), active.teamId());
        assertThat(board.male().totalElements()).isEqualTo(1);
        assertThat(board.female().totalElements()).isEqualTo(1);
        var unknownUser = service.waiting(adminId, 2, Long.MAX_VALUE, 0, 0, 20);
        assertThat(unknownUser.male().items()).isEmpty();
        assertThat(unknownUser.female().items()).isEmpty();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH + "/waiting").param("userId", ordinaryId.toString()).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.male.items[0].team.teamId").value(led.teamId().intValue()))
                .andExpect(jsonPath("$.data.female.items[0].team.teamId").value(active.teamId().intValue()));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingElapsedUsesServerLocalTimeAndLeavesMissingOrFutureTimesNull() {
        LocalDateTime past = LocalDateTime.now().withNano(0).minusSeconds(90);
        var known = waitingFixture(GTeamSize.THREE, GTeamGender.M, past);
        var missing = waitingFixture(GTeamSize.THREE, GTeamGender.M, null);
        var future = waitingFixture(GTeamSize.THREE, GTeamGender.F, LocalDateTime.now().plusDays(1));
        var before = databaseState();
        LocalDateTime beforeRead = LocalDateTime.now();

        var board = service.waiting(adminId, 3, null, 0, 0, 5);

        LocalDateTime afterRead = LocalDateTime.now();
        assertWaitingIds(board.male(), known.teamId(), missing.teamId());
        assertWaitingIds(board.female(), future.teamId());
        assertThat(board.male().items().get(0).elapsedWaitSeconds()).isBetween(
                Duration.between(past, beforeRead).getSeconds(), Duration.between(past, afterRead).getSeconds());
        assertThat(board.male().items().get(1).elapsedWaitSeconds()).isNull();
        assertThat(board.female().items().get(0).elapsedWaitSeconds()).isNull();
        assertThat(databaseState()).isEqualTo(before);
    }

    enum WaitingQueueObservation { UNAVAILABLE, OUTSIDE_SAMPLE, NOT_OBSERVED }

    @ParameterizedTest
    @EnumSource(WaitingQueueObservation.class)
    void waitingPopulationSurvivesUnavailableOrIncompleteRedisObservations(WaitingQueueObservation presence) throws Exception {
        var male = waitingFixture(GTeamSize.TWO, GTeamGender.M, LocalDateTime.now().minusMinutes(2));
        var female = waitingFixture(GTeamSize.TWO, GTeamGender.F, LocalDateTime.now().minusMinutes(2));
        var observation = new AdminGroupMatchingDtos.Queue(presence.name(),
                presence == WaitingQueueObservation.UNAVAILABLE ? null
                        : presence == WaitingQueueObservation.OUTSIDE_SAMPLE ? Long.valueOf(1_500) : Long.valueOf(0),
                presence == WaitingQueueObservation.OUTSIDE_SAMPLE ? 1_000 : 0,
                presence == WaitingQueueObservation.OUTSIDE_SAMPLE, null, 0, 0, Instant.now());
        when(queueObserver.observeMany(eq(GTeamSize.TWO), anyCollection()))
                .thenReturn(Map.of(male.teamId(), observation, female.teamId(), observation));
        var before = databaseState();

        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH + "/waiting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.male.totalElements").value(1))
                .andExpect(jsonPath("$.data.female.totalElements").value(1))
                .andExpect(jsonPath("$.data.male.items[0].queue.presence").value(presence.name()))
                .andExpect(jsonPath("$.data.female.items[0].queue.presence").value(presence.name()));
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void emptyWaitingBoardHasZeroCountsAndSkipsActiveCountQuery() {
        fixture(GTeamSize.TWO, GTemporaryTeamRoomStatus.READY_CHECK, 2);
        var before = databaseState();

        var board = service.waiting(adminId, 2, null, 0, 0, 5);

        assertThat(board.male().items()).isEmpty();
        assertThat(board.female().items()).isEmpty();
        assertThat(board.male().totalElements()).isZero();
        assertThat(board.female().totalElements()).isZero();
        verify(diagnostics, never()).activeCounts(any());
        verify(queueObserver).observeMany(GTeamSize.TWO, List.of());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingHttpDefaultsAndExplicitThreeByThreeReturnNoStoreAndNoPrivateFields() throws Exception {
        LocalDateTime time = LocalDateTime.now().minusMinutes(1);
        var two = waitingFixture(GTeamSize.TWO, GTeamGender.M, time);
        var three = waitingFixture(GTeamSize.THREE, GTeamGender.F, time);
        var before = databaseState();
        authenticate(adminId, UserRole.ADMIN);
        String defaultJson = mvc.perform(get(PATH + "/waiting"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.teamSize").value(2))
                .andExpect(jsonPath("$.data.male.page").value(0))
                .andExpect(jsonPath("$.data.female.page").value(0))
                .andExpect(jsonPath("$.data.male.size").value(5))
                .andExpect(jsonPath("$.data.female.size").value(5))
                .andExpect(jsonPath("$.data.male.items[0].team.teamGender").value("M"))
                .andReturn().getResponse().getContentAsString();
        authenticate(adminId, UserRole.ADMIN);
        String explicitJson = mvc.perform(get(PATH + "/waiting").param("teamSize", "3").param("size", "20"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.teamSize").value(3))
                .andExpect(jsonPath("$.data.female.size").value(20))
                .andExpect(jsonPath("$.data.female.items[0].team.teamGender").value("F"))
                .andReturn().getResponse().getContentAsString();
        for (String json : List.of(defaultJson, explicitJson)) {
            assertNoForbiddenFields(objectMapper.readTree(json));
            assertThat(json).doesNotContain(BODY, two.inviteCode(), two.queueToken(), three.inviteCode(), three.queueToken(),
                    "synthetic-not-deliverable-token", "synthetic existing notification body");
        }
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test
    void waitingRequiresCurrentActiveAdministratorBeforeAnyDiagnosticOrQueueRead() throws Exception {
        var before = databaseState();
        mvc.perform(get(PATH + "/waiting")).andExpect(status().isUnauthorized());
        authenticate(ordinaryId, UserRole.USER);
        mvc.perform(get(PATH + "/waiting")).andExpect(status().isForbidden());
        assertForbidden(() -> service.waiting(null, 2, null, 0, 0, 5));
        assertForbidden(() -> service.waiting(ordinaryId, 2, null, 0, 0, 5));
        assertForbidden(() -> service.waiting(Long.MAX_VALUE, 2, null, 0, 0, 5));
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(diagnostics, queueObserver);

        transaction.executeWithoutResult(tx -> users.findById(adminId).orElseThrow().changeRole(UserRole.USER));
        before = databaseState();
        authenticate(adminId, UserRole.ADMIN);
        mvc.perform(get(PATH + "/waiting")).andExpect(status().isForbidden());
        assertThat(databaseState()).isEqualTo(before);
        verifyNoInteractions(diagnostics, queueObserver);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "RESTRICTED", "DELETED"})
    void waitingRejectsNonActiveAdministratorStatus(UserStatus status) {
        transaction.executeWithoutResult(tx -> ReflectionTestUtils.setField(
                users.findById(adminId).orElseThrow(), "status", status));
        var before = databaseState();

        assertForbidden(() -> service.waiting(adminId, 2, null, 0, 0, 5));

        verifyNoInteractions(diagnostics, queueObserver);
        assertThat(databaseState()).isEqualTo(before);
    }

    enum InvalidWaitingParameter { FORMAT_ONE, FORMAT_FOUR, MALE_PAGE, FEMALE_PAGE, SIZE_ZERO, SIZE_TOO_LARGE, USER_ZERO, USER_NEGATIVE }

    @ParameterizedTest
    @EnumSource(InvalidWaitingParameter.class)
    void waitingRejectsInvalidNumericParametersBeforeQueries(InvalidWaitingParameter invalid) throws Exception {
        int format = invalid == InvalidWaitingParameter.FORMAT_ONE ? 1 : invalid == InvalidWaitingParameter.FORMAT_FOUR ? 4 : 2;
        int malePage = invalid == InvalidWaitingParameter.MALE_PAGE ? -1 : 0;
        int femalePage = invalid == InvalidWaitingParameter.FEMALE_PAGE ? -1 : 0;
        int size = invalid == InvalidWaitingParameter.SIZE_ZERO ? 0 : invalid == InvalidWaitingParameter.SIZE_TOO_LARGE ? 21 : 5;
        Long userId = invalid == InvalidWaitingParameter.USER_ZERO ? Long.valueOf(0)
                : invalid == InvalidWaitingParameter.USER_NEGATIVE ? Long.valueOf(-1) : null;
        var before = databaseState();
        assertInvalid(() -> service.waiting(adminId, format, userId, malePage, femalePage, size));
        authenticate(adminId, UserRole.ADMIN);
        var request = get(PATH + "/waiting").param("teamSize", String.valueOf(format))
                .param("malePage", String.valueOf(malePage)).param("femalePage", String.valueOf(femalePage))
                .param("size", String.valueOf(size));
        if (userId != null) request.param("userId", userId.toString());
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(diagnostics, queueObserver);
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"teamSize,unknown", "malePage,1.5", "femalePage,not-a-page", "userId,not-a-user", "size,not-a-size"})
    void waitingRejectsMalformedHttpParameters(String parameter, String value) throws Exception {
        authenticate(adminId, UserRole.ADMIN);

        mvc.perform(get(PATH + "/waiting").param(parameter, value)).andExpect(status().isBadRequest());

        verifyNoInteractions(diagnostics, queueObserver);
    }

    private void assertWaitingIds(AdminDtos.PageResponse<AdminGroupMatchingDtos.WaitingTeam> page, Long... ids) {
        assertThat(page.items()).extracting(row -> row.team().teamId()).containsExactly(ids);
    }

    private AdminGroupMatchingDtos.Queue queueObservation(Long id) {
        boolean found = "FOUND".equals(queuePresence.get(id));
        return new AdminGroupMatchingDtos.Queue(found ? "FOUND" : "NOT_OBSERVED", found ? 1L : 0L,
                found ? 1 : 0, false, found ? 1 : null, found ? 1 : 0, 0, Instant.now());
    }

    private Fixture waitingFixture(GTeamSize size, GTeamGender gender, LocalDateTime queuedAt) {
        return waitingFixture(size, gender, queuedAt, null);
    }

    private Fixture waitingFixture(GTeamSize size, GTeamGender gender, LocalDateTime queuedAt, Long leaderId) {
        return transaction.execute(tx -> {
            var fixture = fixtureInTransaction(size, GTemporaryTeamRoomStatus.QUEUE_WAITING, size.getValue(), leaderId);
            var team = teams.findById(fixture.teamId()).orElseThrow();
            ReflectionTestUtils.setField(team, "teamGender", gender);
            ReflectionTestUtils.setField(team, "queuedAt", queuedAt);
            return fixture;
        });
    }

    private Fixture fixture(GTeamSize size, GTemporaryTeamRoomStatus status, int readyCount) {
        return transaction.execute(tx -> fixtureInTransaction(size, status, readyCount, null));
    }

    private Fixture fixtureInTransaction(GTeamSize size, GTemporaryTeamRoomStatus status,
                                         int readyCount, Long leaderOverride) {
        int memberCount = status == GTemporaryTeamRoomStatus.OPEN || status == GTemporaryTeamRoomStatus.CANCELLED
                ? 1 : size.getValue();
        List<User> memberUsers = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            memberUsers.add(i == 0 && leaderOverride != null ? users.findById(leaderOverride).orElseThrow()
                    : users.save(user("fixture-member", UserRole.USER)));
        }
        ChatRoom chatRoom = rooms.save(ChatRoom.create("temporary-fixture", ChatRoomType.GROUP));
        chatRoom.updateLastMessage(BODY, LocalDateTime.now());
        Long leader = memberUsers.get(0).getId();
        var team = teams.save(GTemporaryTeamRoom.create(leader, "fixture-team", GTeamGender.M, size,
                GGenderFilter.ANY, GTeamVisibility.PRIVATE, chatRoom.getId()));
        String invitation = "INV-" + team.getId();
        String token = "queue-fixture-" + team.getId();
        team.assignInviteCode(invitation);
        while (team.getCurrentMemberCount() < memberCount) team.addMember();
        for (int i = 0; i < memberUsers.size(); i++) {
            User user = memberUsers.get(i);
            participants.save(GTemporaryTeamMember.create(team.getId(), user.getId(), i == 0));
            var ready = GTeamReadyState.create(team.getId(), user.getId());
            ready.setReady(i < readyCount);
            readiness.save(ready);
            var chatMember = ChatRoomMember.create(chatRoom, user, 7L);
            if (i == 0) chatMember.hide("fixture-hidden");
            chatMembers.save(chatMember);
        }
        messages.save(ChatMessage.create(chatRoom.getId(), leader, "fixture-member", BODY, MessageType.TEXT));
        if (status != GTemporaryTeamRoomStatus.OPEN && status != GTemporaryTeamRoomStatus.CANCELLED) {
            team.enterReadyCheck(leader);
            if (status != GTemporaryTeamRoomStatus.READY_CHECK) {
                team.startQueue(leader, true, token);
                if (status == GTemporaryTeamRoomStatus.MATCHED) team.markMatched();
            }
        }
        if (status == GTemporaryTeamRoomStatus.CANCELLED) team.cancel(leader);
        if (status == GTemporaryTeamRoomStatus.QUEUE_WAITING) queuePresence.put(team.getId(), "FOUND");
        return new Fixture(team.getId(), chatRoom.getId(), memberUsers.stream().map(User::getId).toList(), invitation, token);
    }

    private Pair matchedPair(GTeamSize size, boolean complete) {
        return transaction.execute(tx -> {
            var first = fixtureInTransaction(size, GTemporaryTeamRoomStatus.MATCHED, size.getValue(), null);
            var second = fixtureInTransaction(size, GTemporaryTeamRoomStatus.MATCHED, size.getValue(), null);
            ReflectionTestUtils.setField(teams.findById(second.teamId()).orElseThrow(), "teamGender", GTeamGender.F);
            var result = results.save(GMatchResult.create(first.teamId(), second.teamId()));
            Long finalId = null;
            if (complete) {
                var finalChat = rooms.save(ChatRoom.create("final-fixture", ChatRoomType.GROUP));
                var finalRoom = finalRooms.save(GFinalGroupChatRoom.create(finalChat.getId(), first.teamId(),
                        second.teamId(), result.getId(), size));
                finalId = finalRoom.getId();
                result.completeFinalRoomCreation(finalId);
                for (var fixture : List.of(first, second)) {
                    for (Long id : fixture.userIds()) chatMembers.save(ChatRoomMember.create(finalChat, users.findById(id).orElseThrow()));
                    participants.findByTeamRoomIdOrderByJoinedAtAsc(fixture.teamId()).forEach(GTemporaryTeamMember::markLeft);
                    readiness.deleteByTeamRoomId(fixture.teamId());
                    chatMembers.deleteAllInBatch(chatMembers.findByChatRoomId(fixture.chatRoomId()));
                    teams.findById(fixture.teamId()).orElseThrow().closeAfterFinalRoomCreated();
                }
            }
            return new Pair(first, second, result.getId(), finalId);
        });
    }

    private User user(String nickname, UserRole role) {
        String unique = UUID.randomUUID().toString();
        return User.builder().provider(SocialProvider.KAKAO).socialId(unique)
                .email(unique + "@example.test").nickname(nickname).role(role).status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL).createdAt(LocalDateTime.now()).tickets(10).build();
    }

    private void seedNotificationRows() {
        // Persist synthetic history directly. No notification service, dispatcher or worker
        // is registered; this fixture token/device reference cannot address a real device.
        LocalDateTime fixtureTime = LocalDateTime.of(2026, 9, 1, 0, 0);
        Notification notification = Notification.create(ordinaryId, NotificationType.GROUP_MATCHED,
                "synthetic existing notification", "synthetic existing notification body", null,
                null, null, "{\"fixture\":true}", "group-diagnostic-notification-fixture");
        entityManager.persist(notification);
        entityManager.persist(NotificationOutbox.create(notification.getId(), ordinaryId, 424242L,
                PushProvider.FCM, "synthetic-not-deliverable-token", "synthetic existing outbox",
                "synthetic existing outbox body", "{\"fixture\":true}", fixtureTime));
        entityManager.persist(PushEvent.create(ordinaryId, notification.getId(), "synthetic-provider-message",
                PushEventType.RECEIVED, fixtureTime, "synthetic-not-deliverable-device"));
    }

    private AdminGroupMatchingDtos.Detail detail(Fixture fixture) {
        return service.detail(adminId, fixture.teamId());
    }

    private void authenticate(Long id, UserRole role) {
        var principal = new CustomUserPrincipal(id, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private void assertNoForbiddenFields(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            assertThat(FORBIDDEN_FIELDS).doesNotContain(field.getKey());
            assertNoForbiddenFields(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::assertNoForbiddenFields);
    }

    private Map<String, List<List<String>>> databaseState() {
        // Only this test's synthetic, committed H2 rows. Capture all columns to detect
        // silent token/time/member/readiness/ticket/message mutations, not just row counts.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, List<List<String>>> state = new LinkedHashMap<>();
        for (String table : List.of("users", "matching_temporary_team_rooms", "matching_temporary_team_members",
                "matching_team_ready_states", "matching_results", "matching_final_group_chat_rooms",
                "chat_rooms", "chat_room_members", "chat_messages", "ticket_ledger", "admin_audit_logs",
                "notifications", "notification_outbox", "push_events")) {
            state.put(table, jdbc.query("select * from " + table + " order by id", (rs, rowNumber) -> {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                    row.add(rs.getString(column));
                }
                return row;
            }));
        }
        return state;
    }

    private record Fixture(Long teamId, Long chatRoomId, List<Long> userIds, String inviteCode, String queueToken) {}
    private record Pair(Fixture first, Fixture second, Long resultId, Long finalRoomId) {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.admin", "univ.airconnect.groupmatching.repository",
            "univ.airconnect.chat.repository", "univ.airconnect.user.repository"})
    @Import(AdminGroupMatchingService.class)
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:admin-group-matching-security;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }
}
