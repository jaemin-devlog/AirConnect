package univ.airconnect.global.security.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Slf4j
public class CustomStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    private final StompOpsMonitor stompOpsMonitor;

    public CustomStompErrorHandler(StompOpsMonitor stompOpsMonitor) {
        this.stompOpsMonitor = stompOpsMonitor;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setLeaveMutable(true);

        String message = toSafeMessage(ex);
        errorAccessor.setMessage(message);

        if (clientMessage != null) {
            StompHeaderAccessor clientAccessor = StompHeaderAccessor.wrap(clientMessage);
            errorAccessor.setSessionId(clientAccessor.getSessionId());
            if (clientAccessor.getReceipt() != null) {
                errorAccessor.setReceiptId(clientAccessor.getReceipt());
            }
        }

        log.warn("STOMP ERROR FRAME CREATED: sessionId={}, type={}, message={}",
                errorAccessor.getSessionId(),
                ex.getClass().getSimpleName(),
                message);
        stompOpsMonitor.recordOutboundErrorFrameCreated();

        return org.springframework.messaging.support.MessageBuilder
                .createMessage(EMPTY_PAYLOAD, errorAccessor.getMessageHeaders());
    }

    private String toSafeMessage(Throwable ex) {
        // Spring messaging/conversion exceptions may embed the original message,
        // native Authorization headers, or database details in their text.
        return "STOMP request failed";
    }
}


