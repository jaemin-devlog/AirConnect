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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;

import java.time.LocalDateTime;
import java.util.Objects;

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

    @Column(name = "temp_chat_room_id", nullable = false, unique = true)
    private Long tempChatRoomId;

    @Column(name = "invite_code", unique = true, length = 20)
    private String inviteCode;

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
            Long tempChatRoomId
    ) {
        validateCreateArgs(leaderId, teamName, teamGender, teamSize, opponentGenderFilter, visibility, tempChatRoomId);

        this.leaderId = leaderId;
        this.teamName = teamName.trim();
        this.teamGender = teamGender;
        this.teamSize = teamSize;
        this.currentMemberCount = 1;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        this.opponentGenderFilter = opponentGenderFilter;
        this.visibility = visibility;
        this.tempChatRoomId = tempChatRoomId;
        this.inviteCode = null;
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
            Long tempChatRoomId
    ) {
        return GTemporaryTeamRoom.builder()
                .leaderId(leaderId)
                .teamName(teamName)
                .teamGender(teamGender)
                .teamSize(teamSize)
                .opponentGenderFilter(opponentGenderFilter)
                .visibility(visibility)
                .tempChatRoomId(tempChatRoomId)
                .build();
    }

    public void updateTeamName(Long requestUserId, String newTeamName) {
        validateLeader(requestUserId);
        if (newTeamName == null || newTeamName.isBlank()) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "팀 이름은 비어 있을 수 없습니다.");
        }
        if (status.isTerminal() || status.isQueueing()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "현재 상태에서는 팀 이름을 수정할 수 없습니다.");
        }
        this.teamName = newTeamName.trim();
        touch();
    }

    public void updateVisibility(Long requestUserId, GTeamVisibility visibility) {
        validateLeader(requestUserId);
        if (visibility == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "visibility is required.");
        }
        if (!status.canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "Visibility can only be changed before queueing.");
        }
        this.visibility = visibility;
        touch();
    }

    public void addMember() {
        if (!status.canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_JOIN_NOT_ALLOWED, "현재 상태에서는 팀원을 추가할 수 없습니다.");
        }
        if (isFull()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_FULL, "팀 정원이 가득 찼습니다.");
        }
        this.currentMemberCount++;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void removeMember() {
        if (!status.canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "현재 상태에서는 팀원을 제거할 수 없습니다.");
        }
        if (currentMemberCount <= 1) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "방 인원은 1명 미만으로 줄일 수 없습니다.");
        }
        this.currentMemberCount--;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void enterReadyCheck(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal() || status.isQueueing()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "현재 상태에서는 준비 확인 단계로 전환할 수 없습니다.");
        }
        if (!isFull()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_NOT_FULL, "정원이 모두 차야 준비 확인을 시작할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void reopenForRecruiting(Long requestUserId) {
        validateLeader(requestUserId);
        if (status == GTemporaryTeamRoomStatus.CLOSED || status == GTemporaryTeamRoomStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "종료된 방은 다시 모집 상태로 되돌릴 수 없습니다.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void startQueue(Long requestUserId, boolean allMembersReady, String queueToken) {
        validateLeader(requestUserId);

        if (!status.canEnterQueue()) {
            throw new BusinessException(ErrorCode.READY_CHECK_REQUIRED, "준비 확인 상태에서만 큐에 진입할 수 있습니다.");
        }
        if (!isFull()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_NOT_FULL, "정원이 모두 차야 큐에 진입할 수 있습니다.");
        }
        if (!allMembersReady) {
            throw new BusinessException(ErrorCode.TEAM_NOT_ALL_READY, "모든 팀원이 준비 완료 상태여야 합니다.");
        }
        if (queueToken == null || queueToken.isBlank()) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "queueToken은 비어 있을 수 없습니다.");
        }

        this.queueToken = queueToken;
        this.queuedAt = LocalDateTime.now();
        this.status = GTemporaryTeamRoomStatus.QUEUE_WAITING;
        touch();
    }

    public void leaveQueue() {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new BusinessException(ErrorCode.QUEUE_WAITING_REQUIRED, "큐 대기 중인 팀만 큐에서 나갈 수 있습니다.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void markMatched() {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new BusinessException(ErrorCode.QUEUE_WAITING_REQUIRED, "큐 대기 중인 팀만 매칭 완료 처리할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
        touch();
    }

    public boolean recoverQueueMetadata(String recoveredQueueToken) {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new BusinessException(ErrorCode.QUEUE_WAITING_REQUIRED, "큐 대기 중인 팀만 큐 메타데이터를 복구할 수 있습니다.");
        }
        if (recoveredQueueToken == null || recoveredQueueToken.isBlank()) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "recoveredQueueToken은 비어 있을 수 없습니다.");
        }

        boolean changed = false;

        if (this.queueToken == null || this.queueToken.isBlank()) {
            this.queueToken = recoveredQueueToken;
            changed = true;
        }
        if (this.queuedAt == null) {
            this.queuedAt = LocalDateTime.now();
            changed = true;
        }

        if (changed) {
            touch();
        }
        return changed;
    }

    public void closeAfterFinalRoomCreated() {
        if (this.status != GTemporaryTeamRoomStatus.MATCHED) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "매칭 완료된 팀방만 종료 처리할 수 있습니다.");
        }
        this.status = GTemporaryTeamRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    public void cancel(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_TERMINATED, "이미 종료된 팀방입니다.");
        }
        this.status = GTemporaryTeamRoomStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    public void assignInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED, "inviteCode는 비어 있을 수 없습니다.");
        }
        if (!status.canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID, "현재 상태에서는 초대 코드를 발급할 수 없습니다.");
        }
        this.inviteCode = inviteCode.trim();
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
        if (this.teamGender == other.teamGender) {
            return false;
        }
        return true;
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
            throw new BusinessException(ErrorCode.LEADER_ONLY_ACTION, "방장만 수행할 수 있습니다.");
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
            Long tempChatRoomId
    ) {
        if (leaderId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "leaderId는 필수입니다.");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "teamName은 필수입니다.");
        }
        if (teamGender == null) {
            throw new BusinessException(ErrorCode.TEAM_GENDER_REQUIRED, "teamGender는 필수입니다.");
        }
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED, "teamSize는 필수입니다.");
        }
        if (opponentGenderFilter == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "opponentGenderFilter는 필수입니다.");
        }
        if (visibility == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "visibility는 필수입니다.");
        }
        if (tempChatRoomId == null) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "tempChatRoomId는 필수입니다.");
        }
    }
}
