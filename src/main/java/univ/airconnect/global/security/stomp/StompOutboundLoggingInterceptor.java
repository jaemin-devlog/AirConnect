package univ.airconnect.global.security.stomp;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompOutboundLoggingInterceptor implements ChannelInterceptor {

    private final StompOpsMonitor stompOpsMonitor;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECTED) {
            stompOpsMonitor.recordOutboundConnectedFrame();
            log.info("STOMP OUTBOUND CONNECTED: sessionId={}, user={}",
                    accessor.getSessionId(),
                    accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");
        } else if (command == StompCommand.ERROR) {
            stompOpsMonitor.recordOutboundErrorFrame();
            log.warn("STOMP OUTBOUND ERROR: sessionId={}, message={}",
                    accessor.getSessionId(),
                    accessor.getMessage());
        }

        return message;
    }
}

