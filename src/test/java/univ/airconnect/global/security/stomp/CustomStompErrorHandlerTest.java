package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class CustomStompErrorHandlerTest {

    @Test
    void errorFrameDoesNotEchoAuthorizationPayloadOrDatabaseException() {
        var headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setSessionId("dummy-session");
        headers.setReceipt("dummy-receipt");
        headers.setNativeHeader("Authorization", "Bearer dummy-sensitive-token");
        var request = MessageBuilder.createMessage("dummy-private-chat".getBytes(), headers.getMessageHeaders());
        var handler = new CustomStompErrorHandler(new StompOpsMonitor(20));

        var response = handler.handleClientMessageProcessingError(request,
                new MessageDeliveryException(request, "SQL dummy-private-chat Bearer dummy-sensitive-token"));
        var error = StompHeaderAccessor.wrap(response);

        assertThat(error.getMessage()).isEqualTo("STOMP request failed");
        assertThat(error.getReceiptId()).isEqualTo("dummy-receipt");
        assertThat(error.getSessionId()).isEqualTo("dummy-session");
        assertThat(response.getPayload()).isEmpty();
        assertThat(response.getHeaders().toString()).doesNotContain("dummy-private-chat", "dummy-sensitive-token", "SQL");
    }
}
