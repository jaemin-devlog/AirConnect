package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;

@Component
@RequiredArgsConstructor
public class RealtimeMainStatisticsPublisher {

    private final StatisticsService statisticsService;
    private final SimpMessageSendingOperations messagingTemplate;

    private Snapshot lastPublishedSnapshot;

    @Scheduled(
            fixedDelayString = "${app.statistics.realtime-interval-ms:10000}",
            initialDelayString = "${app.statistics.realtime-initial-delay-ms:10000}"
    )
    public synchronized void publishIfChanged() {
        RealtimeMainStatisticsResponse response = statisticsService.getRealtimeMainStatistics();
        Snapshot currentSnapshot = Snapshot.from(response);
        if (currentSnapshot.equals(lastPublishedSnapshot)) {
            return;
        }

        messagingTemplate.convertAndSend(StompSessionRegistry.MAIN_STATISTICS_DESTINATION, response);
        if (lastPublishedSnapshot == null
                || lastPublishedSnapshot.onlineUserCount() != currentSnapshot.onlineUserCount()) {
            messagingTemplate.convertAndSend(
                    StompSessionRegistry.ONLINE_USERS_DESTINATION,
                    new OnlinePresenceResponse(response.onlineUserCount(), response.updatedAt())
            );
        }
        lastPublishedSnapshot = currentSnapshot;
    }

    private record Snapshot(
            long totalRegisteredUsers,
            long totalMatchSuccessCount,
            int onlineUserCount,
            Long topDepartmentId,
            String topDepartmentName,
            long topDepartmentRequestCount
    ) {
        private static Snapshot from(RealtimeMainStatisticsResponse response) {
            RealtimeMainStatisticsResponse.TopDepartment top = response.topDepartment();
            return new Snapshot(
                    response.totalRegisteredUsers(),
                    response.totalMatchSuccessCount(),
                    response.onlineUserCount(),
                    top == null ? null : top.departmentId(),
                    top == null ? null : top.deptName(),
                    top == null ? 0L : top.requestCount()
            );
        }
    }
}
