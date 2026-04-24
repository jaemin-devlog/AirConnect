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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

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

    @Builder
    private GTeamReadyState(Long teamRoomId, Long userId) {
        if (teamRoomId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "팀방 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "사용자 ID는 필수입니다.");
        }
        this.teamRoomId = teamRoomId;
        this.userId = userId;
        this.ready = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static GTeamReadyState create(Long teamRoomId, Long userId) {
        return GTeamReadyState.builder()
                .teamRoomId(teamRoomId)
                .userId(userId)
                .build();
    }

    public void markReady() {
        this.ready = true;
        this.readyAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markNotReady() {
        this.ready = false;
        this.readyAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void setReady(boolean ready) {
        if (ready) {
            markReady();
            return;
        }
        markNotReady();
    }

    public boolean isReady() {
        return Boolean.TRUE.equals(ready);
    }
}
