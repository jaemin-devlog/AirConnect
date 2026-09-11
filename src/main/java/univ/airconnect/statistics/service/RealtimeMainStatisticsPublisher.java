package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RealtimeMainStatisticsPublisher {

    private final StatisticsService statisticsService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final StompSessionRegistry stompSessionRegistry;
    private final DepartmentRankingSnapshotService departmentRankingSnapshotService;

    private Snapshot lastPublishedSnapshot;
    private Integer lastPublishedOnlineUserCount;
    private List<DepartmentRankingResponse> lastPublishedDepartmentRankings;

    @Scheduled(
            fixedDelayString = "${app.statistics.realtime-interval-ms:60000}",
            initialDelayString = "${app.statistics.realtime-initial-delay-ms:1000}"
    )
    public synchronized void publishIfChanged() {
        List<DepartmentRankingResponse> departmentRankings = departmentRankingSnapshotService.refresh();
        if (!departmentRankings.equals(lastPublishedDepartmentRankings)) {
            messagingTemplate.convertAndSend(
                    StompSessionRegistry.DEPARTMENT_RANKINGS_DESTINATION,
                    departmentRankings
            );
            lastPublishedDepartmentRankings = departmentRankings;
        }

        RealtimeMainStatisticsResponse response = statisticsService.getRealtimeMainStatistics();
        Snapshot currentSnapshot = Snapshot.from(response);
        if (!currentSnapshot.equals(lastPublishedSnapshot)) {
            messagingTemplate.convertAndSend(StompSessionRegistry.MAIN_STATISTICS_DESTINATION, response);
            lastPublishedSnapshot = currentSnapshot;
        }

        int currentOnlineUserCount = stompSessionRegistry.onlineUserCount();
        if (lastPublishedOnlineUserCount == null
                || lastPublishedOnlineUserCount != currentOnlineUserCount) {
            messagingTemplate.convertAndSend(
                    StompSessionRegistry.ONLINE_USERS_DESTINATION,
                    new OnlinePresenceResponse(currentOnlineUserCount, response.updatedAt())
            );
            lastPublishedOnlineUserCount = currentOnlineUserCount;
        }
    }

    private record Snapshot(
            long totalRegisteredUsers,
            long totalMatchSuccessCount,
            long last24HoursActiveUserCount,
            List<RealtimeMainStatisticsResponse.TopDepartment> topDepartments
    ) {
        private static Snapshot from(RealtimeMainStatisticsResponse response) {
            return new Snapshot(
                    response.totalRegisteredUsers(),
                    response.totalMatchSuccessCount(),
                    response.last24HoursActiveUserCount(),
                    List.copyOf(response.topDepartments())
            );
        }
    }
}
