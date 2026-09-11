package univ.airconnect.groupmatching.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "matching_team_ready_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_team_ready_state_room_user", columnNames = {"team_room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_team_ready_state_room_id", columnList = "team_room_id"),
                @Index(name = "idx_team_ready_state_user_id", columnList = "user_id"),
                @Index(name = "idx_team_ready_state_ready", columnList = "is_ready")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GTeamReadyState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_room_id", nullable = false)
    private Long teamRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_ready", nullable = false)
    private Boolean ready;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isReady() {
        return Boolean.TRUE.equals(ready);
    }
}
