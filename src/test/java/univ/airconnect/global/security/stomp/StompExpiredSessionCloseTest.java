package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import univ.airconnect.auth.security.AccessTokenRevocationService;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.repository.UserRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompExpiredSessionCloseTest {

    @Test
    void revokedSocketGetsNoChatPayloadAndSpringClosesAndCleansPresence() throws Exception {
        var revocations = mock(AccessTokenRevocationService.class);
        var registry = new StompSessionRegistry(revocations);
        registry.register("socket", 1L, Instant.now().plusSeconds(300), "fingerprint");
        when(revocations.isRevokedFingerprint("fingerprint")).thenReturn(true);
        var users = mock(UserRepository.class);
        var matching = mock(GMatchingService.class);
        var outbound = new StompOutboundAuthorizationInterceptor(registry, users,
                mock(ChatRoomMemberRepository.class), matching);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("socket");
        headers.setDestination("/sub/chat/room/77");
        headers.setSubscriptionId("subscription");
        var blockedMessage = MessageBuilder.createMessage("private chat".getBytes(), headers.getMessageHeaders());

        var safeError = outbound.preSend(blockedMessage, mock(org.springframework.messaging.MessageChannel.class));
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket");
        when(socket.getAttributes()).thenReturn(new HashMap<>());
        var protocol = new StompSubProtocolHandler();
        protocol.handleMessageToClient(socket, safeError);
        verify(socket).close(CloseStatus.PROTOCOL_ERROR);
        var frame = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(socket).sendMessage(frame.capture());
        assertThat(frame.getValue().getPayload()).contains("ERROR").doesNotContain("private chat");

        var chat = mock(ChatService.class);
        var inbound = new ExecutorSubscribableChannel();
        inbound.setInterceptors(List.of(new StompHandler(mock(JwtProvider.class), chat, matching,
                mock(StompOpsMonitor.class), users, registry)));
        inbound.subscribe(message -> { });
        protocol.afterSessionEnded(socket, CloseStatus.PROTOCOL_ERROR, inbound);
        verify(chat).removeSessionInfo("socket");
        assertThat(registry.findUserId("socket")).isEmpty();
    }
}
