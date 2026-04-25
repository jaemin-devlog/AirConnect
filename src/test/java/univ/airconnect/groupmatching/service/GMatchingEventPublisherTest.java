package univ.airconnect.groupmatching.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.global.tx.AfterCommitExecutor;
import univ.airconnect.groupmatching.dto.response.GMatchingRealtimeEventResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GMatchingEventPublisherTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Test
    void publishQueueSnapshot_sendsMessageToTeamRoomDestination() {
        GMatchingEventPublisher publisher = new GMatchingEventPublisher(messagingTemplate, new AfterCommitExecutor());
        GMatchingService.QueueSnapshot snapshot = new GMatchingService.QueueSnapshot(
                12L,
                "QUEUE_WAITING",
                3,
                2,
                8,
                null,
                null
        );

        publisher.publishQueueSnapshot(snapshot);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/sub/matching/team-room/12"),
                payloadCaptor.capture()
        );

        GMatchingRealtimeEventResponse payload = (GMatchingRealtimeEventResponse) payloadCaptor.getValue();
        assertThat(payload.eventType()).isEqualTo("QUEUE_UPDATED");
        assertThat(payload.teamRoomId()).isEqualTo(12L);
        assertThat(payload.position()).isEqualTo(3);
        assertThat(payload.matched()).isFalse();
    }
}
