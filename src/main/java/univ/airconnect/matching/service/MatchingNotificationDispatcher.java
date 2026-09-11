package univ.airconnect.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.matching.repository.MatchingNotificationEventRepository;
import univ.airconnect.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MatchingNotificationDispatcher {
    private final MatchingNotificationEventRepository events;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    // Notification + push outbox creation and event deletion commit together.
    // On failure the event remains, including after a server restart.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Long id) {
        var event = events.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getNextAttemptAt().isAfter(LocalDateTime.now(Clock.systemUTC()))) return;
        try {
            var command = objectMapper.readValue(event.getCommandJson(), NotificationService.CreateCommand.class);
            notifications.createAndEnqueue(command);
            events.delete(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored matching notification command", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(Long id) {
        events.findByIdForUpdate(id).ifPresent(event -> event.defer());
    }
}
