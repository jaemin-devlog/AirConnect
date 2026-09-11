package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;

import java.time.LocalDateTime;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMainStatisticsPublisherTest {

    @Mock StatisticsService statisticsService;
    @Mock SimpMessageSendingOperations messagingTemplate;

    @Test
    void publishesCombinedSnapshotAndLegacyOnlineSnapshotOnlyWhenValuesChange() {
        RealtimeMainStatisticsPublisher publisher =
                new RealtimeMainStatisticsPublisher(statisticsService, messagingTemplate);
        RealtimeMainStatisticsResponse first = response(100, 20, 7, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse unchanged = response(100, 20, 7, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse changed = response(101, 20, 7, 1L, "시각디자인학과", 56);
        when(statisticsService.getRealtimeMainStatistics()).thenReturn(first, unchanged, changed);

        publisher.publishIfChanged();
        publisher.publishIfChanged();
        publisher.publishIfChanged();

        InOrder order = inOrder(messagingTemplate);
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
                new RealtimeMainStatisticsPublisher(statisticsService, messagingTemplate);
        RealtimeMainStatisticsResponse first = response(100, 20, 7, 1L, "시각디자인학과", 56);
        RealtimeMainStatisticsResponse changed = response(100, 20, 8, 1L, "시각디자인학과", 56);
        when(statisticsService.getRealtimeMainStatistics()).thenReturn(first, changed);

        publisher.publishIfChanged();
        publisher.publishIfChanged();

        verify(messagingTemplate).convertAndSend(
                StompSessionRegistry.ONLINE_USERS_DESTINATION,
                new OnlinePresenceResponse(8, changed.updatedAt())
        );
    }

    private RealtimeMainStatisticsResponse response(
            long users, long matches, int online, Long departmentId, String name, long requests
    ) {
        return new RealtimeMainStatisticsResponse(
                users,
                matches,
                online,
                new RealtimeMainStatisticsResponse.TopDepartment(departmentId, name, requests),
                LocalDateTime.now()
        );
    }
}
