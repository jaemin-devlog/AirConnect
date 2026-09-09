package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.chat.service.RedisSubscriber;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real finalization, ChatService, JPA row locks and ticket-history inserts against dedicated
 * in-memory H2. Fixtures are committed before REQUIRES_NEW finalization. No Boot application,
 * profiles, production resources, Redis server or notification transport is loaded.
 * H2 tests establish transactional invariants, not production MySQL/Redis lock equivalence.
 */
@SpringJUnitConfig(GMatchingTicketConsistencyTest.IsolatedJpaConfig.class)
class GMatchingTicketConsistencyTest {
    private static final String FAILURE_CONSTRAINT = "ck_group_ticket_fixture_failure";

    @Autowired GMatchingService matching;
    @Autowired ChatService chat;
    @Autowired UserRepository users;
    @Autowired GTemporaryTeamRoomRepository teamRooms;
    @Autowired GTeamReadyStateRepository readiness;
    @Autowired GFinalGroupChatRoomRepository finalRooms;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository chatMembers;
    @Autowired ChatMessageRepository messages;
    @Autowired TicketLedgerRepository ticketHistory;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired DataSource dataSource;
    @Autowired EntityManager entityManager;
    @MockitoSpyBean GMatchResultRepository matchResults;
    @MockitoSpyBean GTemporaryTeamMemberRepository teamMembers;

    @MockitoBean RedisTemplate<String, Object> redis;
    @MockitoBean RedisMessageListenerContainer redisListener;
    @MockitoBean RedisSubscriber redisSubscriber;
    @MockitoBean SimpMessageSendingOperations messaging;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserBlockPolicyService blockPolicy;
    @MockitoBean GMatchingEventPublisher matchingEvents;
    @MockitoBean GMatchingPushService matchingPush;
    @MockitoBean AnalyticsService analytics;

    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc = new JdbcTemplate(dataSource);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.opsForList()).thenReturn(mock(ListOperations.class));
        when(redis.opsForSet()).thenReturn(mock(SetOperations.class));
        when(redis.opsForHash()).thenReturn(mock(HashOperations.class));
    }

    @AfterEach
    void cleanCommittedFixtures() {
        reset(matchResults, teamMembers);
        jdbc.execute("alter table ticket_ledger drop constraint if exists " + FAILURE_CONSTRAINT);
        // All data belongs to this test class's dedicated in-memory datasource.
        transaction.executeWithoutResult(status -> {
            entityManager.clear();
            for (String entity : List.of("ChatMessage", "ChatRoomMember", "GFinalGroupChatRoom",
                    "GMatchResult", "GTeamReadyState", "GTemporaryTeamMember", "GTemporaryTeamRoom",
                    "ChatRoom", "TicketLedger", "UserProfile", "User")) {
                entityManager.createQuery("delete from " + entity).executeUpdate();
            }
        });
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    void finalization_createsEveryMembershipAndOneTicketChangePerMember(GTeamSize size) {
        Fixture fixture = committedFixture(size, false);

        assertThat(matching.finalizePendingMatches()).isEqualTo(1);
        assertFinalized(fixture, fixture.beforeTickets());
        assertThat(matching.finalizePendingMatches()).isZero();
        assertFinalized(fixture, fixture.beforeTickets());

        transaction.executeWithoutResult(status -> {
            assertThat(rooms.count()).isEqualTo(3);
            assertThat(finalRooms.count()).isEqualTo(1);
            assertThat(ticketHistory.count()).isEqualTo(size.getValue() * 2L);
            assertThat(messages.count()).isEqualTo(3);
        });
        verify(matchingEvents, times(2)).publishMatched(any());
        verify(notifications, times(size.getValue() * 2)).createAndEnqueue(any());
        verify(matchingPush, never()).notifyMatched(any(), any(), any());
    }

    @Test
    void insufficientTicketsForMiddleMember_rollsBackEveryBalanceAndCreatesNoFinalRoom() {
        Fixture fixture = committedFixture(GTeamSize.TWO, true);

        assertThat(matching.finalizePendingMatches()).isZero();

        assertPendingUnchanged(fixture);
        assertOnlyTemporaryRooms(2);
        verify(matchingEvents, never()).publishMatched(any());
        verify(matchingPush, never()).notifyMatched(any(), any(), any());
    }

    @Test
    void realTicketHistoryConstraintFailure_rollsBackRoomMembershipsAndEarlierMemberChanges() {
        Fixture fixture = committedFixture(GTeamSize.TWO, false);
        rejectTicketHistoryFor(fixture.userIds().get(1));

        assertThat(matching.finalizePendingMatches()).isZero();

        assertPendingUnchanged(fixture);
        assertOnlyTemporaryRooms(2);
        verify(matchingEvents, never()).publishMatched(any());
        verify(matchingPush, never()).notifyMatched(any(), any(), any());
    }

    @Test
    void failedResult_doesNotRollBackTheNextSuccessfulResult() {
        Fixture failed = committedFixture(GTeamSize.TWO, false);
        Fixture succeeded = committedFixture(GTeamSize.THREE, false);
        rejectTicketHistoryFor(failed.userIds().get(1));

        assertThat(matching.finalizePendingMatches()).isEqualTo(1);

        assertPendingUnchanged(failed);
        assertFinalized(succeeded, succeeded.beforeTickets());
        transaction.executeWithoutResult(status -> {
            assertThat(rooms.count()).isEqualTo(5);
            assertThat(finalRooms.count()).isEqualTo(1);
            assertThat(ticketHistory.count()).isEqualTo(6);
        });
    }

    @Test
    void concurrentFinalizationOfSameResult_createsAndChargesOnlyOnce() throws Exception {
        Fixture fixture = committedFixture(GTeamSize.TWO, false);
        var bothSelected = new CyclicBarrier(2);
        // Synchronize only the candidate scan. The result/team/user row locks and all writes
        // remain real. A Spring Data interface spy has no concrete callRealMethod here.
        doAnswer(invocation -> {
            EntityManager reader = entityManagerFactory.createEntityManager();
            List<Long> selected;
            try {
                selected = reader.createQuery("select r.id from GMatchResult r where r.status = :status "
                                + "and r.matchedAt <= :threshold order by r.id", Long.class)
                        .setParameter("status", GMatchResultStatus.MATCHED)
                        .setParameter("threshold", invocation.getArgument(0, LocalDateTime.class))
                        .getResultList();
            } finally {
                reader.close();
            }
            assertThat(selected).containsExactly(fixture.resultId());
            bothSelected.await(10, TimeUnit.SECONDS);
            return selected;
        }).when(matchResults).findPendingFinalizationIds(any(LocalDateTime.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> matching.finalizePendingMatches());
            var second = executor.submit(() -> matching.finalizePendingMatches());
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        } finally {
            stop(executor);
            reset(matchResults);
        }

        assertFinalized(fixture, fixture.beforeTickets());
        transaction.executeWithoutResult(status -> {
            assertThat(rooms.count()).isEqualTo(3);
            assertThat(finalRooms.count()).isEqualTo(1);
            assertThat(ticketHistory.count()).isEqualTo(4);
        });
        verify(matchingEvents, times(2)).publishMatched(any());
    }

    @ParameterizedTest(name = "preloaded User sees concurrent credit of {0} tickets")
    @ValueSource(ints = {2, 5})
    void preloadedUserAndPendingNicknameChange_preserveConcurrentCreditAndFreshTicketSnapshot(int credit)
            throws Exception {
        Fixture fixture = committedFixture(GTeamSize.TWO, false);
        Long userId = fixture.userIds().get(0);
        var staleLoaded = new CountDownLatch(1);
        var creditCommitted = new CountDownLatch(1);
        var firstRead = new AtomicBoolean(true);
        String updatedNickname = "fixture nickname retained";

        // Inject a real preloaded User before the production ticket-lock helper. Reproduce
        // a pending profile change too: flushing it must not overwrite another transaction's
        // credit. The +2 case leaves the final balance equal to the OLD JPA snapshot.
        doAnswer(invocation -> {
            EntityManager current = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            assertThat(current).isNotNull();
            List<GTemporaryTeamMember> result = current.createQuery(
                            "select m from GTemporaryTeamMember m where m.teamRoomId = :id "
                                    + "and m.leftAt is null order by m.joinedAt", GTemporaryTeamMember.class)
                    .setParameter("id", fixture.firstTeamId()).getResultList();
            if (firstRead.compareAndSet(true, false)) {
                User stale = current.find(User.class, userId);
                assertThat(stale.getTickets()).isEqualTo(10);
                ReflectionTestUtils.setField(stale, "nickname", updatedNickname);
                staleLoaded.countDown();
                assertThat(creditCommitted.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(teamMembers).findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(fixture.firstTeamId());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var finalized = executor.submit(() -> matching.finalizePendingMatches());
            assertThat(staleLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            transaction.executeWithoutResult(status -> users.findByIdForTicketUpdate(userId)
                    .orElseThrow().addTickets(credit));
            creditCommitted.countDown();
            assertThat(finalized.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            creditCommitted.countDown();
            stop(executor);
            reset(teamMembers);
        }

        var before = new HashMap<>(fixture.beforeTickets());
        before.put(userId, 10 + credit);
        assertFinalized(fixture, before);
        transaction.executeWithoutResult(status -> {
            User persisted = users.findById(userId).orElseThrow();
            assertThat(persisted.getNickname()).isEqualTo(updatedNickname);
            assertThat(persisted.getTickets()).isEqualTo(10 + credit - 2);
        });
    }

    private Fixture committedFixture(GTeamSize size, boolean insufficientMiddleMember) {
        return transaction.execute(status -> {
            List<Long> ids = new ArrayList<>();
            Map<Long, Integer> balances = new LinkedHashMap<>();
            for (int i = 0; i < size.getValue() * 2; i++) {
                int balance = insufficientMiddleMember && i == 1 ? size.getValue() - 1 : 10;
                User user = users.save(User.builder().provider(SocialProvider.KAKAO)
                        .socialId("ticket-fixture-" + UUID.randomUUID()).nickname("fixture member " + i)
                        .status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                        .tickets(balance).createdAt(LocalDateTime.now()).build());
                ids.add(user.getId());
                balances.put(user.getId(), balance);
            }
            GTemporaryTeamRoom first = matchedTeam(size, GTeamGender.M, ids.subList(0, size.getValue()));
            GTemporaryTeamRoom second = matchedTeam(size, GTeamGender.F,
                    ids.subList(size.getValue(), ids.size()));
            GMatchResult result = GMatchResult.create(first.getId(), second.getId());
            // Set the synthetic time BEFORE the initial INSERT: matchedAt is updatable=false.
            ReflectionTestUtils.setField(result, "matchedAt", LocalDateTime.now().minusSeconds(30));
            matchResults.save(result);
            entityManager.flush();
            return new Fixture(result.getId(), first.getId(), second.getId(), first.getTempChatRoomId(),
                    second.getTempChatRoomId(), size, List.copyOf(ids), Map.copyOf(balances));
        });
    }

    private GTemporaryTeamRoom matchedTeam(GTeamSize size, GTeamGender gender, List<Long> memberIds) {
        var tempChat = chat.createGroupRoomWithMembers("fixture temporary chat", memberIds);
        var room = teamRooms.save(GTemporaryTeamRoom.create(memberIds.get(0), "fixture team " + UUID.randomUUID(),
                gender, size, GGenderFilter.ANY, GTeamVisibility.PUBLIC, tempChat.getId()));
        for (int i = 0; i < memberIds.size(); i++) {
            if (i > 0) room.addMember();
            teamMembers.save(GTemporaryTeamMember.create(room.getId(), memberIds.get(i), i == 0));
            var ready = GTeamReadyState.create(room.getId(), memberIds.get(i));
            ready.markReady();
            readiness.save(ready);
        }
        room.enterReadyCheck(room.getLeaderId());
        room.startQueue(room.getLeaderId(), true, "synthetic-queue-" + UUID.randomUUID());
        room.markMatched();
        return room;
    }

    private void rejectTicketHistoryFor(Long userId) {
        // A real database failure on the second member, not a mocked save exception.
        jdbc.execute("alter table ticket_ledger add constraint " + FAILURE_CONSTRAINT
                + " check (ref_type <> 'GROUP_MATCHING' or user_id <> " + userId + ")");
    }

    private void assertPendingUnchanged(Fixture fixture) {
        transaction.executeWithoutResult(status -> {
            var result = matchResults.findById(fixture.resultId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(GMatchResultStatus.MATCHED);
            assertThat(result.getFinalGroupChatRoomId()).isNull();
            assertThat(finalRooms.findByMatchResultId(fixture.resultId())).isEmpty();
            for (Long teamId : List.of(fixture.firstTeamId(), fixture.secondTeamId())) {
                assertThat(teamRooms.findById(teamId).orElseThrow().getStatus())
                        .isEqualTo(GTemporaryTeamRoomStatus.MATCHED);
                assertThat(teamMembers.countByTeamRoomIdAndLeftAtIsNull(teamId)).isEqualTo(fixture.size().getValue());
                assertThat(readiness.countByTeamRoomId(teamId)).isEqualTo(fixture.size().getValue());
            }
            assertThat(chatMembers.countByChatRoomId(fixture.firstChatId())).isEqualTo(fixture.size().getValue());
            assertThat(chatMembers.countByChatRoomId(fixture.secondChatId())).isEqualTo(fixture.size().getValue());
            for (Long id : fixture.userIds()) {
                assertThat(users.findById(id).orElseThrow().getTickets()).isEqualTo(fixture.beforeTickets().get(id));
            }
            assertThat(historyFor(fixture)).isEmpty();
        });
    }

    private void assertFinalized(Fixture fixture, Map<Long, Integer> expectedBefore) {
        transaction.executeWithoutResult(status -> {
            var result = matchResults.findById(fixture.resultId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo(GMatchResultStatus.FINAL_ROOM_CREATED);
            var finalRoom = finalRooms.findByMatchResultId(fixture.resultId()).orElseThrow();
            assertThat(result.getFinalGroupChatRoomId()).isEqualTo(finalRoom.getId());
            assertThat(rooms.findById(finalRoom.getChatRoomId()).orElseThrow().getType()).isEqualTo(ChatRoomType.GROUP);
            assertThat(chatMembers.findUserIdsByChatRoomId(finalRoom.getChatRoomId()))
                    .containsExactlyInAnyOrderElementsOf(fixture.userIds());
            for (Long teamId : List.of(fixture.firstTeamId(), fixture.secondTeamId())) {
                assertThat(teamRooms.findById(teamId).orElseThrow().getStatus()).isEqualTo(GTemporaryTeamRoomStatus.CLOSED);
                assertThat(teamMembers.countByTeamRoomIdAndLeftAtIsNull(teamId)).isZero();
                assertThat(readiness.countByTeamRoomId(teamId)).isZero();
            }
            assertThat(chatMembers.countByChatRoomId(fixture.firstChatId())).isZero();
            assertThat(chatMembers.countByChatRoomId(fixture.secondChatId())).isZero();
            List<TicketLedger> history = historyFor(fixture);
            assertThat(history).hasSize(fixture.userIds().size());
            assertThat(history).extracting(TicketLedger::getUserId)
                    .containsExactlyInAnyOrderElementsOf(fixture.userIds());
            for (TicketLedger entry : history) {
                int before = expectedBefore.get(entry.getUserId());
                int after = before - fixture.size().getValue();
                assertThat(entry.getRefType()).isEqualTo(LedgerRefType.GROUP_MATCHING);
                assertThat(entry.getRefId()).isEqualTo("group-match:" + fixture.resultId() + ":user:" + entry.getUserId());
                assertThat(entry.getReason()).isEqualTo("GROUP_MATCH");
                assertThat(entry.getChangeAmount()).isEqualTo(-fixture.size().getValue());
                assertThat(entry.getBeforeAmount()).isEqualTo(before);
                assertThat(entry.getAfterAmount()).isEqualTo(after);
                assertThat(entry.getCreatedAt()).isNotNull();
                assertThat(users.findById(entry.getUserId()).orElseThrow().getTickets()).isEqualTo(after);
            }
        });
    }

    private List<TicketLedger> historyFor(Fixture fixture) {
        return ticketHistory.findAll().stream().filter(row -> fixture.userIds().contains(row.getUserId())).toList();
    }

    private void assertOnlyTemporaryRooms(long count) {
        transaction.executeWithoutResult(status -> {
            assertThat(rooms.count()).isEqualTo(count);
            assertThat(finalRooms.count()).isZero();
            assertThat(ticketHistory.count()).isZero();
            assertThat(messages.count()).isZero();
        });
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private record Fixture(Long resultId, Long firstTeamId, Long secondTeamId, Long firstChatId,
                           Long secondChatId, GTeamSize size, List<Long> userIds, Map<Long, Integer> beforeTickets) {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.chat.repository", "univ.airconnect.user.repository",
            "univ.airconnect.groupmatching.repository", "univ.airconnect.iap.repository"})
    @Import({ChatService.class, GMatchingService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:group-ticket-consistency-only;MODE=MySQL;"
                    + "DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("univ.airconnect");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
