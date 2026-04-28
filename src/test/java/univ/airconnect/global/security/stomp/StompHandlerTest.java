package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompHandlerTest {

    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private ChatService chatService;
    @Mock
    private GMatchingService matchingService;
    @Mock
    private StompOpsMonitor stompOpsMonitor;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageChannel messageChannel;

    @Test
    void subscribeToChatRoom_mapsSessionAndSyncsReadState() {
        Long userId = 1L;
        Long roomId = 77L;
        String sessionId = "session-1";
        String subscriptionId = "sub-1";
        User user = activeUser();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatService.isMember(roomId, userId)).thenReturn(true);

        StompHandler handler = new StompHandler(
                jwtProvider,
                chatService,
                matchingService,
                stompOpsMonitor,
                userRepository
        );

        Message<byte[]> message = subscribeMessage(sessionId, subscriptionId, roomId, userId);

        handler.preSend(message, messageChannel);

        verify(chatService).enterChatRoom(String.valueOf(roomId));
        verify(chatService).registerSessionRoomSubscription(sessionId, subscriptionId, String.valueOf(roomId));
        verify(chatService).syncReadStateOnRoomViewed(roomId, userId);
    }

    @Test
    void subscribeToHiddenChatRoomIsRejected() {
        Long userId = 1L;
        Long roomId = 88L;
        User user = activeUser();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatService.isMember(roomId, userId)).thenReturn(false);

        StompHandler handler = new StompHandler(
                jwtProvider,
                chatService,
                matchingService,
                stompOpsMonitor,
                userRepository
        );

        Message<byte[]> message = subscribeMessage("session-hidden", "sub-hidden", roomId, userId);

        assertThatThrownBy(() -> handler.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unsubscribeChatRoom_unregistersSessionSubscription() {
        StompHandler handler = new StompHandler(
                jwtProvider,
                chatService,
                matchingService,
                stompOpsMonitor,
                userRepository
        );

        Message<byte[]> message = unsubscribeMessage("session-3", "sub-3");

        handler.preSend(message, messageChannel);

        verify(chatService).unregisterSessionRoomSubscription("session-3", "sub-3");
    }

    @Test
    void subscribeToOwnChatListIsAllowed() {
        Long userId = 1L;
        User user = activeUser();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StompHandler handler = new StompHandler(
                jwtProvider,
                chatService,
                matchingService,
                stompOpsMonitor,
                userRepository
        );

        handler.preSend(subscribeListMessage("session-list", "sub-list", userId, userId), messageChannel);
    }

    @Test
    void subscribeToAnotherUsersChatListIsRejected() {
        Long userId = 1L;
        User user = activeUser();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StompHandler handler = new StompHandler(
                jwtProvider,
                chatService,
                matchingService,
                stompOpsMonitor,
                userRepository
        );

        assertThatThrownBy(() -> handler.preSend(
                subscribeListMessage("session-list", "sub-list", 2L, userId),
                messageChannel
        )).isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> subscribeMessage(String sessionId, String subscriptionId, Long roomId, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination("/sub/chat/room/" + roomId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(userId, UserRole.USER),
                null,
                List.of()
        ));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeListMessage(String sessionId, String subscriptionId, Long subscribedUserId, Long principalUserId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination("/sub/chat/list/" + subscribedUserId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new CustomUserPrincipal(principalUserId, UserRole.USER),
                null,
                List.of()
        ));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> unsubscribeMessage(String sessionId, String subscriptionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User activeUser() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }
}
