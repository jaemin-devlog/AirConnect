package univ.airconnect.groupmatching.dto.response;

import univ.airconnect.groupmatching.service.GMatchingService;

import java.time.LocalDateTime;

public record GMatchingRealtimeEventResponse(
        String eventType,
        Long teamRoomId,
        String status,
        int position,
        int aheadCount,
        int totalWaitingTeams,
        Long finalGroupRoomId,
        Long finalChatRoomId,
        boolean matched,
        LocalDateTime occurredAt
) {
    public static GMatchingRealtimeEventResponse fromQueueSnapshot(
            String eventType,
            GMatchingService.QueueSnapshot snapshot
    ) {
        boolean matched = snapshot.finalGroupRoomId() != null && snapshot.finalChatRoomId() != null;
        return new GMatchingRealtimeEventResponse(
                eventType,
                snapshot.teamRoomId(),
                snapshot.status(),
                snapshot.position(),
                snapshot.aheadCount(),
                snapshot.totalWaitingTeams(),
                snapshot.finalGroupRoomId(),
                snapshot.finalChatRoomId(),
                matched,
                LocalDateTime.now()
        );
    }
}
