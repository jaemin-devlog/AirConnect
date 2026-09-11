package univ.airconnect.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import univ.airconnect.matching.repository.MatchingNotificationEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingNotificationWorker {
    private final MatchingNotificationEventRepository events;
    private final MatchingNotificationDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${matching.one-to-one.notification-worker-delay-ms:1000}")
    public void dispatchPending() {
        var due = events.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                LocalDateTime.now(Clock.systemUTC()));
        for (var event : due) {
            try {
                dispatcher.dispatch(event.getId());
            } catch (RuntimeException failure) {
                log.error("Matching notification will be retried. eventId={}", event.getId(), failure);
                try {
                    dispatcher.defer(event.getId());
                } catch (RuntimeException retryFailure) {
                    log.error("Failed to defer matching notification. eventId={}", event.getId(), retryFailure);
                }
            }
        }
    }
}
