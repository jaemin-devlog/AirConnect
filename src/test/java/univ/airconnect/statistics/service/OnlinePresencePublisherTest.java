package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.global.security.stomp.OnlineUserCountChangedEvent;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnlinePresencePublisherTest {

    @Mock SimpMessageSendingOperations messagingTemplate;

    @Test
    void publishesOnlineCountToStatisticsDestination() {
        OnlinePresencePublisher publisher = new OnlinePresencePublisher(messagingTemplate);
        LocalDateTime occurredAt = LocalDateTime.now();

        publisher.publish(new OnlineUserCountChangedEvent(7, occurredAt));

        verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.ONLINE_USERS_DESTINATION,
                new OnlinePresenceResponse(7, occurredAt)
        );
    }
}
