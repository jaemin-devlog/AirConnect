package univ.airconnect.matching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "matching_queue_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private MatchingQueueEntry(Long userId, boolean active, LocalDateTime enteredAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.active = active;
        this.enteredAt = enteredAt;
        this.updatedAt = updatedAt;
    }

    public static MatchingQueueEntry create(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return new MatchingQueueEntry(userId, true, now, now);
    }

    public void activateAndRequeue() {
        LocalDateTime now = LocalDateTime.now();
        this.active = true;
        this.enteredAt = now;
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }
}


