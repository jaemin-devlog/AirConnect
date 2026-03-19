package univ.airconnect.groupmatching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 두 임시 팀방의 매칭 결과
 *
 * 규칙
 * - 이 엔티티가 생성되었다는 것은 이미 매칭이 성사되었다는 뜻이다.
 * - 따라서 WAITING 같은 상태를 두지 않는다.
 * - team1RoomId, team2RoomId 는 항상 정규화된 순서(작은 ID 먼저)로 저장한다.
 */
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
            throw new IllegalArgumentException("teamRoomId는 필수입니다.");
        }
        if (Objects.equals(team1RoomId, team2RoomId)) {
            throw new IllegalArgumentException("동일한 팀끼리는 매칭할 수 없습니다.");
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
            throw new IllegalArgumentException("finalGroupChatRoomId는 필수입니다.");
        }
        if (this.status != GMatchResultStatus.MATCHED) {
            throw new IllegalStateException("매칭 성사 상태에서만 최종방 생성 완료 처리할 수 있습니다.");
        }
        this.finalGroupChatRoomId = finalGroupChatRoomId;
        this.status = GMatchResultStatus.FINAL_ROOM_CREATED;
        this.finalRoomCreatedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == GMatchResultStatus.FINAL_ROOM_CREATED) {
            throw new IllegalStateException("이미 최종방 생성이 완료된 매칭 결과는 취소할 수 없습니다.");
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
