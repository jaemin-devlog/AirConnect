package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.controller.ChatController;
import univ.airconnect.chat.controller.ChatRoomController;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.RestAuthenticationEntryPoint;
import univ.airconnect.global.security.jwt.JwtAuthenticationFilter;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.global.security.stomp.StompHandler;
import univ.airconnect.global.security.stomp.StompOpsMonitor;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import javax.sql.DataSource;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * State changes are committed in separate transactions, while the same authenticated STOMP
 * principal/session is reused. Real controller, service and JPA; no Boot config or .env loading.
 * JWT cryptography and external transports are mocked, not the status/membership lookups.
 */
@SpringJUnitConfig(ChatSendStatusSecurityTest.JpaConfig.class)
class ChatSendStatusSecurityTest {
    @Autowired ChatService chatService;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomMemberRepository members;
    @Autowired ChatMessageRepository messages;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RedisTemplate<String, Object> redisTemplate;
    @MockitoBean RedisMessageListenerContainer redisListener;
    @MockitoBean RedisSubscriber redisSubscriber;
    @MockitoBean SimpMessageSendingOperations messagingTemplate;
    @MockitoBean NotificationService notifications;
    @MockitoBean UserBlockPolicyService blockPolicy;
    @MockitoBean GMatchingService matchingService;
    @MockitoBean JwtProvider jwtProvider;
    @Autowired StompSessionRegistry stompSessionRegistry;

    private TransactionTemplate transaction;
    private StompHandler handler;
    private ChatController controller;
    private User sender;
    private Long roomId;
    private MockMvc rest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        SetOperations<String, Object> sets = mock(SetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        transaction.executeWithoutResult(tx -> {
            sender = users.save(user("sender"));
            User recipient = users.save(user("recipient"));
            roomId = chatService.createGroupRoomWithMembers("status-security", List.of(sender.getId(), recipient.getId())).getId();
        });
        when(jwtProvider.getUserId("test-token")).thenReturn(sender.getId());
        handler = new StompHandler(jwtProvider, chatService, matchingService, new StompOpsMonitor(20), users,
                stompSessionRegistry);
        controller = new ChatController(chatService);

        // The production JWT filter and the same authenticated-only rule used for chat REST.
        // Unrelated maintenance/activity/email filters are deliberately not part of this slice.
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new JwtAuthenticationFilter(jwtProvider, users),
                new AnonymousAuthenticationFilter("test-anonymous"),
                new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper)),
                new AuthorizationFilter(AuthenticatedAuthorizationManager.authenticated())));
        rest = MockMvcBuilders.standaloneSetup(new ChatRoomController(chatService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(security).build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        transaction.executeWithoutResult(tx -> {
            messages.deleteAllInBatch();
            members.deleteAllInBatch();
            rooms.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void existingSessionSend_usesCurrentCommittedDatabaseStatus(UserStatus status) {
        Principal connectedPrincipal = connectAndSubscribe();
        changeStatus(status);
        clearSendEffects();

        if (status == UserStatus.ACTIVE) {
            send(connectedPrincipal);
            assertNormalSend();
        } else {
            assertThatThrownBy(() -> send(connectedPrincipal)).isInstanceOf(AccessDeniedException.class);
            assertNoSendEffects();
        }
    }

    @Test
    void suspendedThenReactivated_sameSessionCanSendAgain() {
        Principal connectedPrincipal = connectAndSubscribe();
        changeStatus(UserStatus.SUSPENDED);
        clearSendEffects();
        assertThatThrownBy(() -> send(connectedPrincipal)).isInstanceOf(AccessDeniedException.class);
        assertNoSendEffects();
        transaction.executeWithoutResult(tx -> users.findById(sender.getId()).orElseThrow().reactivate());
        send(connectedPrincipal);
        assertNormalSend();
    }

    @Test
    void brokerDirectSend_isRejectedWithoutCreatingChatMessage() {
        Principal connectedPrincipal = connectAndSubscribe();
        clearSendEffects();
        StompHeaderAccessor directSend = frame(
                StompCommand.SEND, "/sub/chat/room/" + roomId, connectedPrincipal);

        assertThatThrownBy(() -> handler.preSend(
                MessageBuilder.createMessage(new byte[0], directSend.getMessageHeaders()),
                mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);

        assertNoSendEffects();
    }

    @Test
    void brokerDirectSendToAnotherRoom_isRejectedWithoutCreatingChatMessage() {
        Principal connectedPrincipal = connectAndSubscribe();
        clearSendEffects();
        StompHeaderAccessor directSend = frame(
                StompCommand.SEND, "/sub/chat/room/" + (roomId + 999L), connectedPrincipal);

        assertThatThrownBy(() -> handler.preSend(
                MessageBuilder.createMessage(new byte[0], directSend.getMessageHeaders()),
                mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);

        assertNoSendEffects();
    }

    @Test
    void matchingOnlyRestriction_doesNotBlockChat() {
        Principal connectedPrincipal = connectAndSubscribe();
        transaction.executeWithoutResult(tx -> users.findById(sender.getId()).orElseThrow()
                .restrictMatching(LocalDateTime.now().plusDays(1), "matching only"));
        assertThat(users.findById(sender.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        clearSendEffects();
        send(connectedPrincipal);
        assertNormalSend();
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void connectAndSubscribe_followTheExistingStatusPolicy(UserStatus status) {
        Principal connectedPrincipal = connectAndSubscribe();
        changeStatus(status);
        if (status == UserStatus.ACTIVE) {
            assertThatCode(this::connectAndSubscribe).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(this::connectAndSubscribe).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> subscribe(connectedPrincipal)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void restSend_existingJwtIsCheckedAgainstCurrentStatus(UserStatus status) throws Exception {
        connectAndSubscribe(); // Same token was valid before the separately committed sanction.
        changeStatus(status);
        clearSendEffects();
        SecurityContextHolder.clearContext();
        var response = rest.perform(post("/api/v1/chat/rooms/{roomId}/messages", roomId)
                .header("Authorization", "Bearer test-token")
                .contentType("application/json").content("{\"content\":\"hello\"}"));
        if (status == UserStatus.ACTIVE) {
            response.andExpect(status().isOk()).andExpect(jsonPath("success").value(true));
            assertNormalSend();
        } else {
            response.andExpect(status().isUnauthorized()).andExpect(jsonPath("success").value(false));
            assertNoSendEffects();
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "RESTRICTED", "DELETED"})
    void restServiceOverload_rejectsBlockedUserEvenWithoutJwtFilter(UserStatus status) {
        changeStatus(status);
        clearSendEffects();
        SendMessageRequest request = new SendMessageRequest();
        ReflectionTestUtils.setField(request, "content", "hello");
        assertThatThrownBy(() -> chatService.sendMessage(sender.getId(), roomId, request))
                .isInstanceOf(AuthException.class)
                .extracting(ex -> ((AuthException) ex).getErrorCode()).isEqualTo(errorFor(status));
        assertNoSendEffects();
    }

    private Principal connectAndSubscribe() {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId("existing-session");
        connect.setNativeHeader("Authorization", "Bearer test-token");
        connect.setLeaveMutable(true);
        handler.preSend(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()), mock(MessageChannel.class));
        Principal principal = connect.getUser();
        assertThat(principal).isNotNull();
        subscribe(principal);
        return principal;
    }

    private void subscribe(Principal principal) {
        StompHeaderAccessor subscribe = frame(StompCommand.SUBSCRIBE, "/sub/chat/room/" + roomId, principal);
        subscribe.setSubscriptionId("existing-subscription");
        handler.preSend(MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders()), mock(MessageChannel.class));
    }

    private void send(Principal principal) {
        StompHeaderAccessor send = frame(StompCommand.SEND, "/pub/chat/message", principal);
        handler.preSend(MessageBuilder.createMessage(new byte[0], send.getMessageHeaders()), mock(MessageChannel.class));
        ChatMessageRequest request = new ChatMessageRequest();
        ReflectionTestUtils.setField(request, "roomId", roomId);
        ReflectionTestUtils.setField(request, "message", "hello");
        // Dispatch the accepted SEND to the actual mapped controller (without a network broker).
        controller.message(request, principal);
    }

    private StompHeaderAccessor frame(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor frame = StompHeaderAccessor.create(command);
        frame.setSessionId("existing-session");
        frame.setDestination(destination);
        frame.setUser(principal);
        frame.setLeaveMutable(true);
        return frame;
    }

    private void changeStatus(UserStatus status) {
        transaction.executeWithoutResult(tx -> {
            User current = users.findById(sender.getId()).orElseThrow();
            switch (status) {
                case ACTIVE -> current.reactivate();
                case SUSPENDED -> current.suspend(LocalDateTime.now().plusDays(1), "test sanction");
                case DELETED -> current.markDeleted();
                // No public admin action sets this enum; matching-only restriction is a separate field.
                case RESTRICTED -> ReflectionTestUtils.setField(current, "status", UserStatus.RESTRICTED);
            }
        });
    }

    private AuthErrorCode errorFor(UserStatus status) {
        return switch (status) {
            case SUSPENDED -> AuthErrorCode.USER_SUSPENDED;
            case RESTRICTED -> AuthErrorCode.USER_RESTRICTED;
            case DELETED -> AuthErrorCode.USER_DELETED;
            default -> throw new IllegalArgumentException("Not a blocked status");
        };
    }

    private void clearSendEffects() {
        clearInvocations(redisTemplate, redisListener, messagingTemplate, notifications);
    }

    private void assertNormalSend() {
        assertThat(messages.count()).isEqualTo(1);
        assertThat(rooms.findById(roomId).orElseThrow().getLastMessage()).isEqualTo("hello");
        verify(redisTemplate).convertAndSend(eq(roomId.toString()), any());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(startsWith("/sub/chat/list/"), any(Object.class));
        verify(notifications).createAndEnqueue(any(NotificationService.CreateCommand.class));
    }

    private void assertNoSendEffects() {
        assertThat(messages.count()).isZero();
        assertThat(rooms.findById(roomId).orElseThrow().getLastMessage()).isNull();
        verifyNoInteractions(redisTemplate, redisListener, messagingTemplate, notifications);
        // NotificationService is mocked: non-invocation proves its enqueue path was never entered.
        // Also check the isolated database has no notification/outbox rows.
        transaction.executeWithoutResult(tx -> {
            assertThat(entityManager.createQuery("select count(n) from Notification n", Long.class).getSingleResult()).isZero();
            assertThat(entityManager.createQuery("select count(n) from NotificationOutbox n", Long.class).getSingleResult()).isZero();
        });
    }

    private User user(String nickname) {
        return User.builder().provider(SocialProvider.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname(nickname).status(UserStatus.ACTIVE).onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now()).tickets(10).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.chat.repository", "univ.airconnect.user.repository"})
    @Import({univ.airconnect.chat.service.ChatDeliveryService.class, univ.airconnect.chat.service.ChatDeliveryDispatcher.class, ChatService.class, StompSessionRegistry.class})
    static class JpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:chat-send-security;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
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
