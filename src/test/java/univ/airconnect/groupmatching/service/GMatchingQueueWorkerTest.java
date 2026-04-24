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
        when(matchingService.reconcileQueue(GTeamSize.TWO))
                .thenReturn(new GMatchingService.QueueReconcileResult(GTeamSize.TWO, false, false, true, 2, 2));
        when(matchingService.reconcileQueue(GTeamSize.THREE))
                .thenReturn(new GMatchingService.QueueReconcileResult(GTeamSize.THREE, false, false, true, 0, 0));
        when(matchingService.processQueueUntilStable(GTeamSize.TWO)).thenReturn(2);
        when(matchingService.processQueueUntilStable(GTeamSize.THREE)).thenReturn(0);
        when(matchingService.finalizePendingMatches()).thenReturn(1);

        GMatchingQueueWorker worker = new GMatchingQueueWorker(matchingService);
        worker.drainQueues();

        verify(matchingService, times(1)).reconcileQueue(GTeamSize.TWO);
        verify(matchingService, times(1)).reconcileQueue(GTeamSize.THREE);
        verify(matchingService, times(1)).processQueueUntilStable(GTeamSize.TWO);
        verify(matchingService, times(1)).processQueueUntilStable(GTeamSize.THREE);
        verify(matchingService, times(1)).finalizePendingMatches();
    }
}
