package univ.airconnect.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.chat.repository.ChatDeliveryEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDeliveryWorker {
    private final ChatDeliveryEventRepository events;
    private final ChatDeliveryDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${chat.delivery.retry-delay-ms:1000}", scheduler = "chatDeliveryScheduler")
    public void retryPending() {
        for (Long id : events.findDueHeads(LocalDateTime.now(Clock.systemUTC()), PageRequest.of(0, 100))) {
            try { dispatcher.deliver(id); }
            catch (RuntimeException failure) {
                log.warn("Chat delivery retained for retry. eventId={}", id, failure);
                try { dispatcher.defer(id); }
                catch (RuntimeException deferFailure) { log.error("Cannot defer chat delivery {}", id, deferFailure); }
            }
        }
    }
}
