package univ.airconnect.groupmatching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import univ.airconnect.groupmatching.dto.response.GMatchingRealtimeEventResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class GMatchingEventPublisher {

    private static final String MATCHING_TEAM_ROOM_SUB_PREFIX = "/sub/matching/team-room/";

    private final SimpMessageSendingOperations messagingTemplate;

    public void publishQueueSnapshot(GMatchingService.QueueSnapshot snapshot) {
        publish("QUEUE_UPDATED", snapshot);
    }

    public void publishMatched(GMatchingService.QueueSnapshot snapshot) {
        publish("MATCHED", snapshot);
    }

    public void publishStatus(Long teamRoomId, String status) {
        GMatchingService.QueueSnapshot snapshot =
                GMatchingService.QueueSnapshot.statusOnly(teamRoomId, status);
        publish("STATUS_CHANGED", snapshot);
    }

    private void publish(String eventType, GMatchingService.QueueSnapshot snapshot) {
        GMatchingRealtimeEventResponse payload =
                GMatchingRealtimeEventResponse.fromQueueSnapshot(eventType, snapshot);
        String destination = MATCHING_TEAM_ROOM_SUB_PREFIX + snapshot.teamRoomId();
        messagingTemplate.convertAndSend(destination, payload);
        log.info("과팅 실시간 이벤트를 발행했습니다. destination={}, eventType={}", destination, eventType);
    }
}
