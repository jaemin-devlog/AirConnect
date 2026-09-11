package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import univ.airconnect.global.security.stomp.OnlineUserCountChangedEvent;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;

@Component
@RequiredArgsConstructor
public class OnlinePresencePublisher {

    private final SimpMessageSendingOperations messagingTemplate;

    @EventListener
    public void publish(OnlineUserCountChangedEvent event) {
        messagingTemplate.convertAndSend(
                StompSessionRegistry.ONLINE_USERS_DESTINATION,
                new OnlinePresenceResponse(event.onlineUserCount(), event.occurredAt())
        );
    }
}
