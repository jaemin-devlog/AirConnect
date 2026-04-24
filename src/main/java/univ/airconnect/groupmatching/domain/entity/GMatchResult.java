package univ.airconnect.groupmatching.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import univ.airconnect.groupmatching.domain.GMatchResultStatus;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "matching_results",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_result_team_pair", columnNames = {"team1_room_id", "team2_room_id"}),
                @UniqueConstraint(name = "uk_match_result_final_room", columnNames = {"final_group_chat_room_id"})
        },
        indexes = {
                @Index(name = "idx_match_result_team1_room_id", columnList = "team1_room_id"),
                @Index(name = "idx_match_result_team2_room_id", columnList = "team2_room_id"),
                @Index(name = "idx_match_result_status", columnList = "status"),
                @Index(name = "idx_match_result_matched_at", columnList = "matched_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GMatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team1_room_id", nullable = false)
    private Long team1RoomId;

    @Column(name = "team2_room_id", nullable = false)
    private Long team2RoomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GMatchResultStatus status;

    @Column(name = "final_group_chat_room_id", unique = true)
    private Long finalGroupChatRoomId;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private LocalDateTime matchedAt;

    @Column(name = "final_room_created_at")
    private LocalDateTime finalRoomCreatedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GMatchResult(Long team1RoomId, Long team2RoomId) {
        if (team1RoomId == null || team2RoomId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "팀방 ID는 필수입니다.");
        }
        if (Objects.equals(team1RoomId, team2RoomId)) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "동일한 팀끼리는 매칭할 수 없습니다.");
        }

        Long smaller = Math.min(team1RoomId, team2RoomId);
        Long larger = Math.max(team1RoomId, team2RoomId);

        this.team1RoomId = smaller;
        this.team2RoomId = larger;
        this.status = GMatchResultStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static GMatchResult create(Long firstTeamRoomId, Long secondTeamRoomId) {
        return GMatchResult.builder()
                .team1RoomId(firstTeamRoomId)
                .team2RoomId(secondTeamRoomId)
                .build();
    }

    public void completeFinalRoomCreation(Long finalGroupChatRoomId) {
        if (finalGroupChatRoomId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "최종 그룹 채팅방 ID는 필수입니다.");
        }
        if (this.status != GMatchResultStatus.MATCHED) {
            throw new BusinessException(ErrorCode.MATCH_RESULT_STATE_INVALID, "매칭 성사 상태에서만 최종방 생성 완료 처리할 수 있습니다.");
        }
        this.finalGroupChatRoomId = finalGroupChatRoomId;
        this.status = GMatchResultStatus.FINAL_ROOM_CREATED;
        this.finalRoomCreatedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == GMatchResultStatus.FINAL_ROOM_CREATED) {
            throw new BusinessException(ErrorCode.MATCH_RESULT_STATE_INVALID, "이미 최종방 생성이 완료된 매칭 결과는 취소할 수 없습니다.");
        }
        this.status = GMatchResultStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean involves(Long teamRoomId) {
        return Objects.equals(team1RoomId, teamRoomId) || Objects.equals(team2RoomId, teamRoomId);
    }

    public Long getOpponentTeamRoomId(Long teamRoomId) {
        if (Objects.equals(team1RoomId, teamRoomId)) {
            return team2RoomId;
        }
        if (Objects.equals(team2RoomId, teamRoomId)) {
            return team1RoomId;
        }
        return null;
    }
}
