package univ.airconnect.chat.service;

import org.junit.jupiter.api.Test;
import univ.airconnect.chat.repository.ChatDeliveryEventRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatDeliveryWorkerTest {
    @Test void stuckHeadIsAttemptedOnlyOncePerRun() {
        var events = mock(ChatDeliveryEventRepository.class);
        var dispatcher = mock(ChatDeliveryDispatcher.class);
        when(events.findDueHeads(any(), any())).thenReturn(List.of(1L));
        doThrow(new IllegalStateException("offline")).when(dispatcher).deliver(1L);
        doThrow(new IllegalStateException("cannot defer")).when(dispatcher).defer(1L);
        new ChatDeliveryWorker(events, dispatcher).retryPending();
        verify(dispatcher, times(1)).deliver(1L);
        verify(dispatcher, times(1)).defer(1L);
    }

    @Test void continuouslyGrowingBacklogHasBoundedWorkPerRun() {
        var events = mock(ChatDeliveryEventRepository.class);
        var dispatcher = mock(ChatDeliveryDispatcher.class);
        var next = new AtomicLong();
        when(events.findDueHeads(any(), any())).thenAnswer(call -> List.of(next.incrementAndGet()));
        new ChatDeliveryWorker(events, dispatcher).retryPending();
        verify(dispatcher, times(1000)).deliver(anyLong());
    }
}
