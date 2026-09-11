package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompOutboundAuthorizationInterceptorTest {

    @Mock StompSessionRegistry sessionRegistry;
    @Mock UserRepository userRepository;
    @Mock ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock GMatchingService matchingService;
    @Mock MessageChannel channel;

    private StompOutboundAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompOutboundAuthorizationInterceptor(
                sessionRegistry, userRepository, chatRoomMemberRepository, matchingService);
    }

    @Test
    void currentChatMemberReceivesOutboundMessage() {
        allowActiveSession("session-1", 1L);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(77L, 1L))
                .thenReturn(true);
        Message<byte[]> message = outbound("session-1", "/sub/chat/room/77");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void userWhoseChatMembershipWasRemovedReceivesNothing() {
        allowActiveSession("session-1", 1L);
        when(chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(77L, 1L))
                .thenReturn(false);

        assertThat(interceptor.preSend(outbound("session-1", "/sub/chat/room/77"), channel)).isNull();
    }

    @Test
    void revokedSessionReceivesNothingEvenWhenOldSubscriptionStillExists() {
        when(sessionRegistry.findUserId("session-revoked")).thenReturn(Optional.empty());

        assertThat(interceptor.preSend(
                outbound("session-revoked", "/sub/chat/room/77"), channel)).isNull();
    }

    @Test
    void expelledMatchingMemberReceivesNothing() {
        allowActiveSession("session-team", 1L);
        when(matchingService.canSubscribeTeamRoom(41L, 1L)).thenReturn(false);

        assertThat(interceptor.preSend(
                outbound("session-team", "/sub/matching/team-room/41"), channel)).isNull();
    }

    @Test
    void anotherUsersChatListIsNeverDelivered() {
        allowActiveSession("session-list", 1L);

        assertThat(interceptor.preSend(
                outbound("session-list", "/sub/chat/list/2"), channel)).isNull();
    }

    @Test
    void activeUserReceivesOnlineUserCount() {
        when(sessionRegistry.findUserId("session-online")).thenReturn(Optional.of(1L));
        Message<byte[]> message = outbound("session-online", "/sub/statistics/online");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
        verifyNoInteractions(userRepository);
    }

    private void allowActiveSession(String sessionId, Long userId) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(sessionRegistry.findUserId(sessionId)).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private Message<byte[]> outbound(String sessionId, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-1");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
