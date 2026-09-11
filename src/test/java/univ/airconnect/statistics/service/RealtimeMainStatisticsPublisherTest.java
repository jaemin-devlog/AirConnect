package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMainStatisticsPublisherTest {

    @Mock StatisticsService statisticsService;
    @Mock SimpMessageSendingOperations messagingTemplate;
    @Mock StompSessionRegistry stompSessionRegistry;
    @Mock DepartmentRankingSnapshotService departmentRankingSnapshotService;

    @Test
    void publishesCombinedSnapshotAndLegacyOnlineSnapshotOnlyWhenValuesChange() {
        RealtimeMainStatisticsPublisher publisher =
                new RealtimeMainStatisticsPublisher(
                        statisticsService, messagingTemplate, stompSessionRegistry,
                        departmentRankingSnapshotService);
        RealtimeMainStatisticsResponse first = response(100, 20, 7, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse unchanged = response(100, 20, 7, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse changed = response(101, 20, 7, 1L, "시각디자인학과", 56);
        when(statisticsService.getRealtimeMainStatistics()).thenReturn(first, unchanged, changed);
        when(stompSessionRegistry.onlineUserCount()).thenReturn(7);
        List<DepartmentRankingResponse> rankings = rankings(56L);
        when(departmentRankingSnapshotService.refresh()).thenReturn(rankings);

        publisher.publishIfChanged();
        publisher.publishIfChanged();
        publisher.publishIfChanged();

        InOrder order = inOrder(messagingTemplate);
        order.verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.DEPARTMENT_RANKINGS_DESTINATION, rankings);
        order.verify(messagingTemplate).convertAndSend(StompSessionRegistry.MAIN_STATISTICS_DESTINATION, first);
        order.verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.ONLINE_USERS_DESTINATION,
                new OnlinePresenceResponse(7, first.updatedAt())
        );
        order.verify(messagingTemplate).convertAndSend(StompSessionRegistry.MAIN_STATISTICS_DESTINATION, changed);
        order.verifyNoMoreInteractions();
    }

    @Test
    void onlineChangeAlsoUpdatesLegacyDestination() {
        RealtimeMainStatisticsPublisher publisher =
                new RealtimeMainStatisticsPublisher(
                        statisticsService, messagingTemplate, stompSessionRegistry,
                        departmentRankingSnapshotService);
        RealtimeMainStatisticsResponse first = response(100, 20, 9, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse unchanged = response(100, 20, 9, 1L, "시각디자인학과", 56);
        when(statisticsService.getRealtimeMainStatistics()).thenReturn(first, unchanged);
        when(stompSessionRegistry.onlineUserCount()).thenReturn(7, 8);
        when(departmentRankingSnapshotService.refresh()).thenReturn(rankings(56L));

        publisher.publishIfChanged();
        publisher.publishIfChanged();

        verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.ONLINE_USERS_DESTINATION,
                new OnlinePresenceResponse(8, unchanged.updatedAt())
        );
    }

    @Test
    void changedDepartmentRankingsArePublishedEvenWithoutViewers() {
        RealtimeMainStatisticsPublisher publisher =
                new RealtimeMainStatisticsPublisher(
                        statisticsService, messagingTemplate, stompSessionRegistry,
                        departmentRankingSnapshotService);
        RealtimeMainStatisticsResponse response = response(100, 20, 9, 1L, "시각디자인학과", 56);
        List<DepartmentRankingResponse> first = rankings(56L);
        List<DepartmentRankingResponse> changed = rankings(57L);
        when(statisticsService.getRealtimeMainStatistics()).thenReturn(response);
        when(stompSessionRegistry.onlineUserCount()).thenReturn(0);
        when(departmentRankingSnapshotService.refresh()).thenReturn(first, changed);

        publisher.publishIfChanged();
        publisher.publishIfChanged();

        verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.DEPARTMENT_RANKINGS_DESTINATION, changed);
    }

    private RealtimeMainStatisticsResponse response(
            long users, long matches, long last24HoursActiveUsers,
            Long departmentId, String name, long requests
    ) {
        return new RealtimeMainStatisticsResponse(
                users,
                matches,
                last24HoursActiveUsers,
                List.of(new RealtimeMainStatisticsResponse.TopDepartment(
                        1, departmentId, name, requests)),
                LocalDateTime.now()
        );
    }

    private List<DepartmentRankingResponse> rankings(long requestCount) {
        return List.of(DepartmentRankingResponse.builder()
                .rank(1)
                .departmentId(1L)
                .deptName("시각디자인학과")
                .requestCount(requestCount)
                .build());
    }
}
