package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses Spring's real simple broker and subscription registry. Repository and domain services are
 * mocked only at the authorization boundary so each test can commit a permission change without
 * replacing broker behavior.
 */
@ExtendWith(MockitoExtension.class)
class StompBrokerSecurityIntegrationTest {

    @Mock JwtProvider jwtProvider;
    @Mock ChatService chatService;
    @Mock GMatchingService matchingService;
    @Mock StompOpsMonitor stompOpsMonitor;
    @Mock UserRepository userRepository;
    @Mock ChatRoomMemberRepository chatRoomMemberRepository;

    private final StompSessionRegistry sessionRegistry = new StompSessionRegistry();
    private ExecutorSubscribableChannel clientInboundChannel;
    private ExecutorSubscribableChannel clientOutboundChannel;
    private ExecutorSubscribableChannel brokerChannel;
    private SimpleBrokerMessageHandler broker;
    private BlockingQueue<Message<?>> delivered;
    private StompHandler inboundAuthorization;

    @BeforeEach
    void setUp() {
        SyncTaskExecutor executor = new SyncTaskExecutor();
        clientInboundChannel = new ExecutorSubscribableChannel(executor);
        clientOutboundChannel = new ExecutorSubscribableChannel(executor);
        brokerChannel = new ExecutorSubscribableChannel(executor);

        inboundAuthorization = new StompHandler(jwtProvider, chatService, matchingService,
                stompOpsMonitor, userRepository, sessionRegistry);
        StompOutboundAuthorizationInterceptor outboundAuthorization =
                new StompOutboundAuthorizationInterceptor(sessionRegistry, userRepository,
                        chatRoomMemberRepository, matchingService);
        clientOutboundChannel.setInterceptors(List.of(outboundAuthorization));

        delivered = new LinkedBlockingQueue<>();
        clientOutboundChannel.subscribe(delivered::add);

        broker = new SimpleBrokerMessageHandler(
                clientInboundChannel, clientOutboundChannel, brokerChannel, List.of("/sub"));
        broker.start();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void exactAuthorizedSubscriptionReceivesBrokerMessage() throws Exception {
        allowActiveSession("member-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(77L, 1L))
                .thenReturn(true);

        subscribe("member-session", 1L, "/sub/chat/room/77");
        publish("/sub/chat/room/77");

        assertThat(delivered.poll(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void nonMemberAndWildcardSubscriptionsNeverReachBroker() {
        allowActiveSession("blocked-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(false);

        assertDenied(() -> subscribe("blocked-session", 1L, "/sub/chat/room/77"));
        assertDenied(() -> subscribe("blocked-session", 1L, "/sub/**"));

        publish("/sub/chat/room/77");
        assertThat(delivered).isEmpty();
    }

    @Test
    void clientCannotSendDirectlyToBrokerDestination() {
        allowActiveSession("member-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(true);
        subscribe("member-session", 1L, "/sub/chat/room/77");

        assertDenied(() -> send("member-session", 1L, "/sub/chat/room/77"));

        assertThat(delivered).isEmpty();
    }

    @Test
    void memberOfAnotherRoomCannotInjectEventIntoSubscribedRoom() {
        allowActiveSession("receiver-session", 2L);
        when(chatService.isMember(20L, 2L)).thenReturn(true);
        subscribe("receiver-session", 2L, "/sub/chat/room/20");
        allowActiveSession("attacker-session", 1L);

        assertDenied(() -> send("attacker-session", 1L, "/sub/chat/room/20"));

        assertThat(delivered).isEmpty();
    }

    @Test
    void leaveKeepsOldBrokerSubscriptionButDeliversZeroMessages() {
        allowActiveSession("leaver-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(true);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(77L, 1L))
                .thenReturn(false);
        subscribe("leaver-session", 1L, "/sub/chat/room/77");

        publish("/sub/chat/room/77");
        assertThat(delivered.poll()).isNull();
    }

    @Test
    void logoutRevokesSessionWhileOldBrokerSubscriptionDeliversZeroMessages() {
        allowActiveSession("logout-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(true);
        subscribe("logout-session", 1L, "/sub/chat/room/77");

        assertThat(sessionRegistry.revokeUser(1L)).isEqualTo(1);
        publish("/sub/chat/room/77");

        assertThat(delivered).isEmpty();
    }

    @Test
    void deletedAccountKeepsOldBrokerSubscriptionButReceivesZeroMessages() {
        allowActiveSession("deleted-session", 1L);
        when(chatService.isMember(77L, 1L)).thenReturn(true);
        subscribe("deleted-session", 1L, "/sub/chat/room/77");
        User deletedUser = mock(User.class);
        when(deletedUser.getStatus()).thenReturn(UserStatus.DELETED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(deletedUser));

        publish("/sub/chat/room/77");

        assertThat(delivered).isEmpty();
    }

    @Test
    void expelledMemberKeepsOldBrokerSubscriptionButReceivesZeroMessages() {
        allowActiveSession("expelled-session", 1L);
        when(matchingService.canSubscribeTeamRoom(41L, 1L)).thenReturn(true, false);
        subscribe("expelled-session", 1L, "/sub/matching/team-room/41");

        publish("/sub/matching/team-room/41");

        assertThat(delivered).isEmpty();
    }

    private void allowActiveSession(String sessionId, Long userId) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        sessionRegistry.register(sessionId, userId);

        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId(sessionId);
        connect.setUser(authentication(userId));
        broker.handleMessage(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()));
        delivered.clear();
    }

    private void subscribe(String sessionId, Long userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("subscription-" + sessionId);
        accessor.setDestination(destination);
        accessor.setUser(authentication(userId));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        broker.handleMessage(inboundAuthorization.preSend(message, clientInboundChannel));
    }

    private void send(String sessionId, Long userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setUser(authentication(userId));
        Message<byte[]> message = MessageBuilder.createMessage("attack".getBytes(), accessor.getMessageHeaders());
        broker.handleMessage(inboundAuthorization.preSend(message, clientInboundChannel));
    }

    private void publish(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        broker.handleMessage(MessageBuilder.createMessage("event".getBytes(), accessor.getMessageHeaders()));
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        CustomUserPrincipal principal = new CustomUserPrincipal(userId, UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void assertDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfAny(MessageDeliveryException.class, AccessDeniedException.class);
    }
}
