package univ.airconnect.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.chat.repository.ChatDeliveryEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDeliveryWorker {
    private final ChatDeliveryEventRepository events;
    private final ChatDeliveryDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${chat.delivery.retry-delay-ms:1000}", scheduler = "chatDeliveryScheduler")
    public void retryPending() {
        Set<Long> attempted = new HashSet<>();
        // Re-query heads after each round so a recovered room drains without 1s per event.
        // Cap work and avoid spinning on a failed/deferred/concurrently locked head.
        while (attempted.size() < 1000) {
            var heads = events.findDueHeads(LocalDateTime.now(Clock.systemUTC()),
                    PageRequest.of(0, Math.min(100, 1000 - attempted.size())));
            boolean progressed = false;
            for (Long id : heads) {
                if (!attempted.add(id)) continue;
                progressed = true;
                try { dispatcher.deliver(id); }
                catch (RuntimeException failure) {
                    log.warn("Chat delivery retained for retry. eventId={}", id, failure);
                    try { dispatcher.defer(id); }
                    catch (RuntimeException deferFailure) { log.error("Cannot defer chat delivery {}", id, deferFailure); }
                }
            }
            if (!progressed) break;
        }
    }
}
