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
import java.util.Objects;

@Entity
@Table(
        name = "matching_temporary_team_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temp_team_member_room_user", columnNames = {"team_room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_temp_team_member_room_id", columnList = "team_room_id"),
                @Index(name = "idx_temp_team_member_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GTemporaryTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_room_id", nullable = false)
    private Long teamRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_leader", nullable = false)
    private Boolean leader;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GTemporaryTeamMember(Long teamRoomId, Long userId, Boolean leader) {
        if (teamRoomId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "teamRoomId는 필수입니다.");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "userId는 필수입니다.");
        }
        this.teamRoomId = teamRoomId;
        this.userId = userId;
        this.leader = leader != null && leader;
        this.joinedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static GTemporaryTeamMember create(Long teamRoomId, Long userId, boolean leader) {
        return GTemporaryTeamMember.builder()
                .teamRoomId(teamRoomId)
                .userId(userId)
                .leader(leader)
                .build();
    }

    public void markLeft() {
        if (leftAt != null) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "이미 퇴장 처리된 멤버입니다.");
        }
        this.leftAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isLeader() {
        return Boolean.TRUE.equals(leader);
    }

    public boolean isActiveMember() {
        return leftAt == null;
    }

    public boolean belongsTo(Long teamRoomId) {
        return Objects.equals(this.teamRoomId, teamRoomId);
    }
}
