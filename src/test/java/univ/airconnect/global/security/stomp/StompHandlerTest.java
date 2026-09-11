package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompHandlerTest {

    @Mock JwtProvider jwtProvider;
    @Mock ChatService chatService;
    @Mock GMatchingService matchingService;
    @Mock StompOpsMonitor stompOpsMonitor;
    @Mock UserRepository userRepository;
    @Mock StompSessionRegistry sessionRegistry;
    @Mock MessageChannel messageChannel;

    private StompHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StompHandler(jwtProvider, chatService, matchingService,
                stompOpsMonitor, userRepository, sessionRegistry);
    }

    @Test
    void subscribeToChatRoomMapsSessionWithoutMarkingMessagesRead() {
        Long userId = 1L;
        Long roomId = 77L;
        String sessionId = "session-1";
        allowActiveSession(sessionId, userId);
        when(chatService.isMember(roomId, userId)).thenReturn(true);

        handler.preSend(frame(StompCommand.SUBSCRIBE, sessionId, "sub-1",
                "/sub/chat/room/77", userId), messageChannel);

        verify(chatService).enterChatRoom("77");
        verify(chatService).registerSessionRoomSubscription(sessionId, "sub-1", "77");
        verify(chatService, never()).syncReadStateOnRoomViewed(roomId, userId);
    }

    @Test
    void subscribeToHiddenChatRoomIsRejected() {
        allowActiveSession("session-hidden", 1L);
        when(chatService.isMember(88L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "session-hidden", "sub-hidden", "/sub/chat/room/88", 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeToOwnChatListIsAllowed() {
        allowActiveSession("session-list", 1L);

        assertThatCode(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "session-list", "sub-list", "/sub/chat/list/1", 1L), messageChannel))
                .doesNotThrowAnyException();
    }

    @Test
    void subscribeToAnotherUsersChatListIsRejected() {
        allowActiveSession("session-list", 1L);

        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "session-list", "sub-list", "/sub/chat/list/2", 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeToAuthorizedMatchingTeamRoomIsAllowed() {
        allowActiveSession("session-team", 1L);
        when(matchingService.canSubscribeTeamRoom(41L, 1L)).thenReturn(true);

        handler.preSend(frame(StompCommand.SUBSCRIBE, "session-team", "sub-team",
                "/sub/matching/team-room/41", 1L), messageChannel);

        verify(matchingService).canSubscribeTeamRoom(41L, 1L);
    }

    @Test
    void subscribeToOnlineUserCountIsAllowedForActiveSession() {
        allowActiveSession("session-online", 1L);

        assertThatCode(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "session-online", "sub-online", "/sub/statistics/online", 1L), messageChannel))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/sub/**", "/sub/chat/**", "/sub/chat/room/*", "/sub/chat/room/**",
            "/sub", "/sub/chat", "/sub/chat/room", "/sub/chat/room/0",
            "/sub/chat/room/-1", "/sub/chat/room/123/extra",
            "/sub/chat/list/1/extra", "/sub/matching/team-room/*", "/sub/unknown"
    })
    void wildcardMalformedAndUnknownSubscriptionsAreDenied(String destination) {
        allowActiveSession("session-invalid", 1L);

        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "session-invalid", "sub-invalid", destination, 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void exactChatSendDestinationIsAllowed() {
        allowActiveSession("session-send", 1L);

        assertThatCode(() -> handler.preSend(frame(StompCommand.SEND,
                "session-send", null, "/pub/chat/message", 1L), messageChannel))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/sub/chat/room/77", "/sub/**", "/pub/unknown",
            "/pub/chat/message/extra", "/queue/admin"
    })
    void clientSendToBrokerOrUnknownDestinationIsDenied(String destination) {
        allowActiveSession("session-send", 1L);

        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SEND,
                "session-send", null, destination, 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokedSessionCannotSubscribeOrSend() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessionRegistry.isAuthorized("revoked", 1L)).thenReturn(false);

        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SUBSCRIBE,
                "revoked", "sub", "/sub/chat/list/1", 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> handler.preSend(frame(StompCommand.SEND,
                "revoked", null, "/pub/chat/message", 1L), messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unsubscribeChatRoomUnregistersSessionSubscription() {
        handler.preSend(frame(StompCommand.UNSUBSCRIBE, "session-3", "sub-3", null, null),
                messageChannel);

        verify(chatService).unregisterSessionRoomSubscription("session-3", "sub-3");
    }

    private void allowActiveSession(String sessionId, Long userId) {
        User user = activeUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRegistry.isAuthorized(sessionId, userId)).thenReturn(true);
    }

    private Message<byte[]> frame(
            StompCommand command, String sessionId, String subscriptionId,
            String destination, Long userId
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (subscriptionId != null) accessor.setSubscriptionId(subscriptionId);
        if (destination != null) accessor.setDestination(destination);
        if (userId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    new CustomUserPrincipal(userId, UserRole.USER), null, List.of()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User activeUser() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }
}
