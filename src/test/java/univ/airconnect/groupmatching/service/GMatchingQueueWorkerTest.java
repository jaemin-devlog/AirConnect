package univ.airconnect.groupmatching.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.groupmatching.domain.GTeamSize;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GMatchingQueueWorkerTest {

    @Mock
    private GMatchingService matchingService;

    @Test
    void drainQueues_stopsEachQueueWhenNoMoreMatches() {
        GMatchingService.MatchSuccessResult result =
                new GMatchingService.MatchSuccessResult(1L, 2L, 3L, 4L, 5L);

        when(matchingService.processQueue(GTeamSize.TWO, 100))
                .thenReturn(result)
                .thenReturn(null);
        when(matchingService.processQueue(GTeamSize.THREE, 100))
                .thenReturn(null);

        GMatchingQueueWorker worker = new GMatchingQueueWorker(matchingService);
        worker.drainQueues();

        verify(matchingService, times(2)).processQueue(GTeamSize.TWO, 100);
        verify(matchingService, times(1)).processQueue(GTeamSize.THREE, 100);
    }
}
