package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "matching_notification_events", indexes = {
        @Index(name = "idx_matching_notification_due", columnList = "next_attempt_at,id")
})
@Getter
@NoArgsConstructor
public class MatchingNotificationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    private Long actorUserId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String commandJson;
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(nullable = false)
    private int attempts;

    public static MatchingNotificationEvent create(Long userId, Long actorUserId, String commandJson) {
        MatchingNotificationEvent event = new MatchingNotificationEvent();
        event.userId = userId;
        event.actorUserId = actorUserId;
        event.commandJson = commandJson;
        event.nextAttemptAt = LocalDateTime.now(Clock.systemUTC());
        return event;
    }

    public void defer() {
        attempts++;
        nextAttemptAt = LocalDateTime.now(Clock.systemUTC())
                .plusSeconds(Math.min(300, 5L * Math.min(attempts, 60)));
    }
}
