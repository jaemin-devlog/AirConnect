package univ.airconnect.groupmatching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus;
import univ.airconnect.groupmatching.domain.GTeamSize;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 최종 그룹 채팅방
 *
 * 확정 UX 규칙
 * - 수락/거절 단계 없음
 * - 매칭 성공 시 즉시 생성
 * - 기존 임시 팀방은 MATCHED -> CLOSED 로 종료 처리
 */
@Entity
@Table(
        name = "matching_final_group_chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_final_group_team_pair", columnNames = {"team1_room_id", "team2_room_id"}),
                @UniqueConstraint(name = "uk_final_group_chat_room", columnNames = {"chat_room_id"}),
                @UniqueConstraint(name = "uk_final_group_match_result", columnNames = {"match_result_id"})
        },
        indexes = {
                @Index(name = "idx_final_group_chat_room_id", columnList = "chat_room_id"),
                @Index(name = "idx_final_group_team1_room_id", columnList = "team1_room_id"),
                @Index(name = "idx_final_group_team2_room_id", columnList = "team2_room_id"),
                @Index(name = "idx_final_group_match_result_id", columnList = "match_result_id"),
                @Index(name = "idx_final_group_status", columnList = "status"),
                @Index(name = "idx_final_group_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GFinalGroupChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 실제 채팅 시스템의 ChatRoom ID
     */
    @Column(name = "chat_room_id", nullable = false, unique = true)
    private Long chatRoomId;

    @Column(name = "team1_room_id", nullable = false)
    private Long team1RoomId;

    @Column(name = "team2_room_id", nullable = false)
    private Long team2RoomId;

    @Column(name = "match_result_id", nullable = false, unique = true)
    private Long matchResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_size", nullable = false, length = 10)
    private GTeamSize teamSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GFinalGroupRoomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GFinalGroupChatRoom(
            Long chatRoomId,
            Long team1RoomId,
            Long team2RoomId,
            Long matchResultId,
            GTeamSize teamSize
    ) {
        if (chatRoomId == null) {
            throw new IllegalArgumentException("chatRoomId는 필수입니다.");
        }
        if (team1RoomId == null || team2RoomId == null) {
            throw new IllegalArgumentException("teamRoomId는 필수입니다.");
        }
        if (Objects.equals(team1RoomId, team2RoomId)) {
            throw new IllegalArgumentException("동일한 팀끼리는 최종 그룹방을 만들 수 없습니다.");
        }
        if (matchResultId == null) {
            throw new IllegalArgumentException("matchResultId는 필수입니다.");
        }
        if (teamSize == null) {
            throw new IllegalArgumentException("teamSize는 필수입니다.");
        }

        Long smaller = Math.min(team1RoomId, team2RoomId);
        Long larger = Math.max(team1RoomId, team2RoomId);

        this.chatRoomId = chatRoomId;
        this.team1RoomId = smaller;
        this.team2RoomId = larger;
        this.matchResultId = matchResultId;
        this.teamSize = teamSize;
        this.status = GFinalGroupRoomStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static GFinalGroupChatRoom create(
            Long chatRoomId,
            Long team1RoomId,
            Long team2RoomId,
            Long matchResultId,
            GTeamSize teamSize
    ) {
        return GFinalGroupChatRoom.builder()
                .chatRoomId(chatRoomId)
                .team1RoomId(team1RoomId)
                .team2RoomId(team2RoomId)
                .matchResultId(matchResultId)
                .teamSize(teamSize)
                .build();
    }

    public void end() {
        if (this.status != GFinalGroupRoomStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태의 최종 그룹방만 종료할 수 있습니다.");
        }
        this.status = GFinalGroupRoomStatus.ENDED;
        this.endedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != GFinalGroupRoomStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태의 최종 그룹방만 취소할 수 있습니다.");
        }
        this.status = GFinalGroupRoomStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == GFinalGroupRoomStatus.ACTIVE;
    }

    public boolean containsTeam(Long teamRoomId) {
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

    public int getFinalMemberCount() {
        return teamSize.getValue() * 2;
    }
}
