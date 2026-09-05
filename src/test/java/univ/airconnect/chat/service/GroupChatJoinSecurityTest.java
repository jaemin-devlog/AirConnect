package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.controller.ChatRoomController;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.global.security.stomp.StompHandler;
import univ.airconnect.global.security.stomp.StompOpsMonitor;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.groupmatching.service.GMatchingEventPublisher;
import univ.airconnect.groupmatching.service.GMatchingPushService;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real controllers/services/JPA with in-memory H2. No Spring Boot application configuration,
 * .env, production datasource, Redis server, or external notification service is loaded.
 * Authentication is supplied at the already-authenticated principal boundary (not JWT validation).
 */
@SpringJUnitConfig(GroupChatJoinSecurityTest.IsolatedJpaConfig.class)
@Transactional
class GroupChatJoinSecurityTest {
    @Autowired ChatService chatService;
    @Autowired GMatchingService matchingService;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository members;
    @Autowired ChatMessageRepository messages;
    @Autowired GTemporaryTeamRoomRepository teamRooms;
    @Autowired GMatchResultRepository matchResults;
    @Autowired GFinalGroupChatRoomRepository finalRooms;
    @Autowired EntityManager entityManager;

    @MockitoBean RedisTemplate<String, Object> redisTemplate;
    @MockitoBean RedisMessageListenerContainer redisListener;
    @MockitoBean RedisSubscriber redisSubscriber;
    @MockitoBean SimpMessageSendingOperations messagingTemplate;
    @MockitoBean NotificationService notificationService;
    @MockitoBean UserBlockPolicyService blockPolicy;
    @MockitoBean GMatchingEventPublisher matchingEvents;
    @MockitoBean GMatchingPushService matchingPush;
    @MockitoBean AnalyticsService analytics;

    private MockMvc mvc;
    private boolean committedMatchingFixture;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        committedMatchingFixture = false;
        // Redis transport is mocked, but memberships and both services are real.
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        ListOperations<String, Object> lists = mock(ListOperations.class);
        SetOperations<String, Object> sets = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForList()).thenReturn(lists);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        // Preserve queue membership/order through real DB states without running a Redis server.
        when(lists.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation ->
                teamRooms.findAll().stream()
                        .filter(room -> room.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING)
                        .map(room -> (Object) room.getId().toString()).toList());
        mvc = MockMvcBuilders.standaloneSetup(new ChatRoomController(chatService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        if (committedMatchingFixture) {
            // The finalizer owns a REQUIRES_NEW transaction. Roll back only the later
            // read/send security checks, then remove its committed, isolated H2 fixtures.
            if (TestTransaction.isActive()) {
                TestTransaction.flagForRollback();
                TestTransaction.end();
            }
            TestTransaction.start();
            try {
                entityManager.clear();
                for (String entity : List.of("ChatMessage", "ChatRoomMember", "GFinalGroupChatRoom",
                        "GMatchResult", "GTeamReadyState", "GTemporaryTeamMember", "GTemporaryTeamRoom",
                        "ChatRoom", "TicketLedger", "UserProfile", "User")) {
                    entityManager.createQuery("delete from " + entity).executeUpdate();
                }
                TestTransaction.flagForCommit();
            } finally {
                TestTransaction.end();
            }
        }
    }

    @Test
    void unrelatedGroupJoin_isForbiddenAndDoesNotCreateMembership() throws Exception {
        User a = saveUser("a", Gender.MALE);
        User b = saveUser("b", Gender.FEMALE);
        User attacker = saveUser("attacker", Gender.MALE);
        ChatRoom room = chatService.createGroupRoomWithMembers("A and B", List.of(a.getId(), b.getId()));
        authenticate(attacker);

        assertJoinForbidden(room.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(members.findUserIdsByChatRoomId(room.getId()))
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(chatService.isMember(room.getId(), attacker.getId())).isFalse();
    }

    @Test
    void guessedRoomId_failedJoinCannotReadMessagesOrSubscribe() throws Exception {
        User a = saveUser("a", Gender.MALE);
        User b = saveUser("b", Gender.FEMALE);
        User attacker = saveUser("attacker", Gender.MALE);
        ChatRoom room = chatService.createGroupRoomWithMembers("Private conversation", List.of(a.getId(), b.getId()));
        authenticate(a);
        mvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", room.getId())
                        .contentType("application/json").content("{\"content\":\"private message\"}"))
                .andExpect(status().isOk());
        authenticate(attacker);

        assertJoinForbidden(room.getId());
        mvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", room.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("success").value(false));
        mvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", room.getId())
                        .contentType("application/json").content("{\"content\":\"intrusion\"}"))
                .andExpect(status().isForbidden());
        assertThat(members.existsByChatRoomIdAndUserId(room.getId(), attacker.getId())).isFalse();
        assertThat(messages.findAll()).hasSize(1);

        StompHandler handler = new StompHandler(mock(JwtProvider.class), chatService, matchingService,
                new StompOpsMonitor(20), users);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("attacker-session");
        accessor.setSubscriptionId("attacker-subscription");
        accessor.setDestination("/sub/chat/room/" + room.getId());
        accessor.setUser(SecurityContextHolder.getContext().getAuthentication());
        accessor.setLeaveMutable(true);
        Message<byte[]> subscription = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> handler.preSend(subscription, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No permission to subscribe this chat room.");
        verifyNoInteractions(redisListener);
    }

    @Test
    void personalDirectJoin_keepsExistingBadRequestAndMembers() throws Exception {
        User a = saveUser("a", Gender.MALE);
        User b = saveUser("b", Gender.FEMALE);
        User attacker = saveUser("attacker", Gender.MALE);
        Long roomId = chatService.createChatRoom("personal", ChatRoomType.PERSONAL, a.getId(), b.getId()).getId();
        for (User user : List.of(a, attacker)) {
            authenticate(user);
            mvc.perform(post("/api/v1/chat/rooms/{roomId}/join", roomId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("success").value(false))
                    .andExpect(jsonPath("error.code").value("COMMON-001"));
        }
        assertThat(members.findUserIdsByChatRoomId(roomId)).containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void groupExistingMemberCannotUseDirectJoinEither() throws Exception {
        User a = saveUser("a", Gender.MALE);
        ChatRoom room = chatService.createGroupRoomWithMembers("group", List.of(a.getId()));
        authenticate(a);
        assertJoinForbidden(room.getId());
        assertThat(members.countByChatRoomId(room.getId())).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    void normalGroupMatching_createsFinalMembershipsAndAllowsEveryMemberToReadAndSend(GTeamSize size) throws Exception {
        List<User> firstUsers = new ArrayList<>();
        List<User> secondUsers = new ArrayList<>();
        for (int i = 0; i < size.getValue(); i++) {
            firstUsers.add(saveUser("first-" + i, Gender.MALE));
            secondUsers.add(saveUser("second-" + i, Gender.FEMALE));
        }
        GTemporaryTeamRoom first = matchingService.createTemporaryTeamRoom(firstUsers.get(0).getId(), "First team",
                GTeamGender.M, size, GGenderFilter.ANY, GTeamVisibility.PUBLIC);
        GTemporaryTeamRoom second = matchingService.createTemporaryTeamRoom(secondUsers.get(0).getId(), "Second team",
                GTeamGender.F, size, GGenderFilter.ANY, GTeamVisibility.PRIVATE);
        for (int i = 1; i < size.getValue(); i++) {
            matchingService.joinPublicRoom(first.getId(), firstUsers.get(i).getId());
            matchingService.joinRoomByInviteCode(second.getInviteCode(), secondUsers.get(i).getId());
        }
        assertThat(members.findUserIdsByChatRoomId(first.getTempChatRoomId()))
                .containsExactlyInAnyOrderElementsOf(firstUsers.stream().map(User::getId).toList());
        assertThat(members.findUserIdsByChatRoomId(second.getTempChatRoomId()))
                .containsExactlyInAnyOrderElementsOf(secondUsers.stream().map(User::getId).toList());
        firstUsers.forEach(user -> matchingService.updateReadyState(first.getId(), user.getId(), true));
        secondUsers.forEach(user -> matchingService.updateReadyState(second.getId(), user.getId(), true));
        matchingService.startMatching(first.getId(), first.getLeaderId());
        matchingService.startMatching(second.getId(), second.getLeaderId());
        assertThat(matchResults.findByStatus(GMatchResultStatus.MATCHED)).hasSize(1);
        GMatchResult result = matchResults.findByStatus(GMatchResultStatus.MATCHED).get(0);
        // Advance only the stored timestamp, avoiding sleeps and preserving production delay semantics.
        entityManager.flush();
        // matchedAt is updatable=false, so changing the managed Java field alone would
        // not persist the synthetic clock advance for a new finalization transaction.
        entityManager.createNativeQuery("update matching_results set matched_at = :matchedAt where id = :id")
                .setParameter("matchedAt", LocalDateTime.now().minusSeconds(15))
                .setParameter("id", result.getId()).executeUpdate();
        committedMatchingFixture = true;
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(matchingService.finalizePendingMatches()).isEqualTo(1);
        TestTransaction.start();
        entityManager.flush();
        entityManager.clear();

        Long finalRoomId = finalRooms.findAll().get(0).getChatRoomId();
        assertThat(rooms.findById(finalRoomId).orElseThrow().getType()).isEqualTo(ChatRoomType.GROUP);
        List<User> allUsers = new ArrayList<>(firstUsers);
        allUsers.addAll(secondUsers);
        assertThat(members.findUserIdsByChatRoomId(finalRoomId))
                .containsExactlyInAnyOrderElementsOf(allUsers.stream().map(User::getId).toList());
        assertThat(members.countByChatRoomId(first.getTempChatRoomId())).isZero();
        assertThat(members.countByChatRoomId(second.getTempChatRoomId())).isZero();
        for (User user : allUsers) {
            authenticate(user);
            assertThat(chatService.isMember(finalRoomId, user.getId())).isTrue();
            mvc.perform(post("/api/v1/chat/rooms/{roomId}/messages", finalRoomId)
                            .contentType("application/json").content("{\"content\":\"hello-" + user.getId() + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("success").value(true));
        }
        for (User user : allUsers) {
            authenticate(user);
            mvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", finalRoomId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("data.length()").value(allUsers.size() + 1));
        }
        User attacker = saveUser("final-room-attacker", Gender.MALE);
        authenticate(attacker);
        assertJoinForbidden(finalRoomId);
        assertThat(members.countByChatRoomId(finalRoomId)).isEqualTo(allUsers.size());
    }

    private void assertJoinForbidden(Long roomId) throws Exception {
        mvc.perform(post("/api/v1/chat/rooms/{roomId}/join", roomId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("success").value(false))
                .andExpect(jsonPath("error.code").value("AUTH-002"));
    }

    private void authenticate(User user) {
        CustomUserPrincipal principal = new CustomUserPrincipal(user.getId(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User saveUser(String nickname, Gender gender) {
        User user = users.save(User.builder().provider(SocialProvider.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname(nickname).status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                .tickets(10).createdAt(LocalDateTime.now()).build());
        profiles.save(UserProfile.create(user, null, null, null, null, gender, null, null, null, null, null));
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.chat.repository", "univ.airconnect.user.repository",
            "univ.airconnect.groupmatching.repository", "univ.airconnect.iap.repository"})
    @Import({ChatService.class, GMatchingService.class})
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:group-join-security;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
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
