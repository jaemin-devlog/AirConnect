package univ.airconnect.groupmatching.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 확정 UX 기준 임시 팀방
 *
 * 역할
 * 1) 팀 구성의 기준 엔티티
 * 2) 준비 완료 체크의 대상 엔티티
 * 3) 서버 큐 등록 상태의 스냅샷 보관
 *
 * 주의
 * - 실제 대기열 순서/앞 팀 수의 진실 원천은 Redis 큐여야 한다.
 * - 이 엔티티의 queueToken, queuedAt 은 DB 상관관계 추적용 메타데이터다.
 */
@Entity
@Table(
        name = "matching_temporary_team_rooms",
        indexes = {
                @Index(name = "idx_temp_team_room_leader_id", columnList = "leader_id"),
                @Index(name = "idx_temp_team_room_status", columnList = "status"),
                @Index(name = "idx_temp_team_room_team_size", columnList = "team_size"),
                @Index(name = "idx_temp_team_room_visibility", columnList = "visibility"),
                @Index(name = "idx_temp_team_room_queue_token", columnList = "queue_token"),
                @Index(name = "idx_temp_team_room_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GTemporaryTeamRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_gender", nullable = false, length = 10)
    private GTeamGender teamGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_size", nullable = false, length = 10)
    private GTeamSize teamSize;

    @Column(name = "current_member_count", nullable = false)
    private Integer currentMemberCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GTemporaryTeamRoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_gender_filter", nullable = false, length = 10)
    private GGenderFilter opponentGenderFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private GTeamVisibility visibility;

    /**
     * 기존 채팅 ChatRoom 엔티티 ID.
     * 임시 팀 전용 채팅방과 1:1로 연결한다.
     */
    @Column(name = "temp_chat_room_id", nullable = false, unique = true)
    private Long tempChatRoomId;

    /**
     * PRIVATE 방일 때만 사용한다.
     */
    @Column(name = "invite_code", unique = true, length = 20)
    private String inviteCode;

    /**
     * UX 범위 밖이지만, 현재 프로젝트 호환성을 위해 유지 가능한 옵션 필드.
     */
    @Column(name = "age_range_min")
    private Integer ageRangeMin;

    @Column(name = "age_range_max")
    private Integer ageRangeMax;

    /**
     * Redis 큐 엔트리와 DB를 연결하기 위한 추적 키.
     */
    @Column(name = "queue_token", length = 100)
    private String queueToken;

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GTemporaryTeamRoom(
            Long leaderId,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Long tempChatRoomId,
            Integer ageRangeMin,
            Integer ageRangeMax
    ) {
        validateCreateArgs(leaderId, teamName, teamGender, teamSize, opponentGenderFilter, visibility, tempChatRoomId, ageRangeMin, ageRangeMax);

        this.leaderId = leaderId;
        this.teamName = teamName.trim();
        this.teamGender = teamGender;
        this.teamSize = teamSize;
        this.currentMemberCount = 1;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        this.opponentGenderFilter = opponentGenderFilter;
        this.visibility = visibility;
        this.tempChatRoomId = tempChatRoomId;
        this.inviteCode = visibility.isPrivate() ? generateInviteCode() : null;
        this.ageRangeMin = ageRangeMin;
        this.ageRangeMax = ageRangeMax;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static GTemporaryTeamRoom create(
            Long leaderId,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Long tempChatRoomId,
            Integer ageRangeMin,
            Integer ageRangeMax
    ) {
        return GTemporaryTeamRoom.builder()
                .leaderId(leaderId)
                .teamName(teamName)
                .teamGender(teamGender)
                .teamSize(teamSize)
                .opponentGenderFilter(opponentGenderFilter)
                .visibility(visibility)
                .tempChatRoomId(tempChatRoomId)
                .ageRangeMin(ageRangeMin)
                .ageRangeMax(ageRangeMax)
                .build();
    }

    public void updateTeamName(Long requestUserId, String newTeamName) {
        validateLeader(requestUserId);
        if (newTeamName == null || newTeamName.isBlank()) {
            throw new IllegalArgumentException("팀 이름은 비어 있을 수 없습니다.");
        }
        if (status.isTerminal() || status.isQueueing()) {
            throw new IllegalStateException("현재 상태에서는 팀 이름을 수정할 수 없습니다.");
        }
        this.teamName = newTeamName.trim();
        touch();
    }

    public void addMember() {
        if (!status.canModifyMembers()) {
            throw new IllegalStateException("현재 상태에서는 팀원을 추가할 수 없습니다.");
        }
        if (isFull()) {
            throw new IllegalStateException("팀 정원이 가득 찼습니다.");
        }
        this.currentMemberCount++;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void removeMember() {
        if (!status.canModifyMembers()) {
            throw new IllegalStateException("현재 상태에서는 팀원을 제거할 수 없습니다.");
        }
        if (currentMemberCount <= 1) {
            throw new IllegalStateException("팀장은 혼자 남더라도 방은 유지되어야 하므로 1명 미만으로 줄일 수 없습니다.");
        }
        this.currentMemberCount--;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void enterReadyCheck(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal() || status.isQueueing()) {
            throw new IllegalStateException("현재 상태에서는 준비 확인 단계로 전환할 수 없습니다.");
        }
        if (!isFull()) {
            throw new IllegalStateException("정원이 모두 차야 준비 확인을 시작할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void reopenForRecruiting(Long requestUserId) {
        validateLeader(requestUserId);
        if (status == GTemporaryTeamRoomStatus.CLOSED || status == GTemporaryTeamRoomStatus.CANCELLED) {
            throw new IllegalStateException("종료된 방은 다시 모집 상태로 돌릴 수 없습니다.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    /**
     * 전원 준비 완료 검증은 GTeamReadyState 집계를 통해 서비스 계층에서 수행하고,
     * 그 결과만 boolean allMembersReady 로 주입받는다.
     */
    public void startQueue(Long requestUserId, boolean allMembersReady, String queueToken) {
        validateLeader(requestUserId);

        if (!status.canEnterQueue()) {
            throw new IllegalStateException("준비 확인 상태에서만 큐에 진입할 수 있습니다.");
        }
        if (!isFull()) {
            throw new IllegalStateException("정원이 모두 차야 큐에 진입할 수 있습니다.");
        }
        if (!allMembersReady) {
            throw new IllegalStateException("팀원 전원이 준비 완료여야 합니다.");
        }
        if (queueToken == null || queueToken.isBlank()) {
            throw new IllegalArgumentException("queueToken은 비어 있을 수 없습니다.");
        }

        this.queueToken = queueToken;
        this.queuedAt = LocalDateTime.now();
        this.status = GTemporaryTeamRoomStatus.QUEUE_WAITING;
        touch();
    }

    public void leaveQueue(Long requestUserId) {
        validateLeader(requestUserId);
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new IllegalStateException("큐 대기 중인 팀만 큐에서 나갈 수 있습니다.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void markMatched() {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new IllegalStateException("큐 대기 중인 팀만 매칭 완료 처리할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
        touch();
    }

    public void closeAfterFinalRoomCreated() {
        if (this.status != GTemporaryTeamRoomStatus.MATCHED) {
            throw new IllegalStateException("매칭 완료된 팀만 종료 처리할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    public void cancel(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal()) {
            throw new IllegalStateException("이미 종료된 팀방입니다.");
        }
        this.status = GTemporaryTeamRoomStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    public void refreshInviteCode(Long requestUserId) {
        validateLeader(requestUserId);
        if (!visibility.isPrivate()) {
            throw new IllegalStateException("공개방은 초대 코드를 재생성할 수 없습니다.");
        }
        if (status.isTerminal()) {
            throw new IllegalStateException("종료된 팀방은 초대 코드를 재생성할 수 없습니다.");
        }
        this.inviteCode = generateInviteCode();
        touch();
    }

    public boolean canMatchWith(GTemporaryTeamRoom other) {
        if (other == null) {
            return false;
        }
        if (Objects.equals(this.id, other.id)) {
            return false;
        }
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING || other.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            return false;
        }
        if (this.teamSize != other.teamSize) {
            return false;
        }
        return this.opponentGenderFilter.allows(other.teamGender)
                && other.opponentGenderFilter.allows(this.teamGender);
    }

    public boolean isFull() {
        return currentMemberCount >= teamSize.getValue();
    }

    public boolean isLeader(Long userId) {
        return Objects.equals(this.leaderId, userId);
    }

    public boolean isPublicRoom() {
        return visibility == GTeamVisibility.PUBLIC;
    }

    public boolean isPrivateRoom() {
        return visibility == GTeamVisibility.PRIVATE;
    }

    private void validateLeader(Long requestUserId) {
        if (!isLeader(requestUserId)) {
            throw new IllegalStateException("방장만 수행할 수 있습니다.");
        }
    }

    private void clearQueueMetadata() {
        this.queueToken = null;
        this.queuedAt = null;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateCreateArgs(
            Long leaderId,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Long tempChatRoomId,
            Integer ageRangeMin,
            Integer ageRangeMax
    ) {
        if (leaderId == null) {
            throw new IllegalArgumentException("leaderId는 필수입니다.");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("teamName은 필수입니다.");
        }
        if (teamGender == null) {
            throw new IllegalArgumentException("teamGender는 필수입니다.");
        }
        if (teamSize == null) {
            throw new IllegalArgumentException("teamSize는 필수입니다.");
        }
        if (opponentGenderFilter == null) {
            throw new IllegalArgumentException("opponentGenderFilter는 필수입니다.");
        }
        if (visibility == null) {
            throw new IllegalArgumentException("visibility는 필수입니다.");
        }
        if (tempChatRoomId == null) {
            throw new IllegalArgumentException("tempChatRoomId는 필수입니다.");
        }
        if ((ageRangeMin == null) != (ageRangeMax == null)) {
            throw new IllegalArgumentException("나이 범위는 최소/최대를 함께 입력해야 합니다.");
        }
        if (ageRangeMin != null && ageRangeMin > ageRangeMax) {
            throw new IllegalArgumentException("ageRangeMin은 ageRangeMax보다 클 수 없습니다.");
        }
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
