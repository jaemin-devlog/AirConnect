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

/**
 * 怨쇳똿 ?꾩떆 ?諛??뷀떚?곕떎.
 *
 * ???뷀떚?곕뒗 ? 援ъ꽦 ?곹깭, 以鍮??꾨즺 ?④퀎, 留ㅼ묶 ??吏꾩엯 ?곹깭,
 * 理쒖쥌 諛??앹꽦 ?댄썑 醫낅즺 ?곹깭瑜??④퍡 蹂닿??쒕떎.
 * ?ㅼ젣 ?湲??쒖꽌? ??? ?섏쓽 湲곗?? Redis ?먯씠硫?
 * queueToken怨?queuedAt? DB 異붿쟻??硫뷀??곗씠?곕줈留??ъ슜?쒕떎.
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
     * ?꾩떆諛??대? 梨꾪똿???곌껐??ChatRoom ID??
     * ?꾩떆 ?諛⑷낵 梨꾪똿諛⑹? 1:1濡??곌껐?쒕떎.
     */
    @Column(name = "temp_chat_room_id", nullable = false, unique = true)
    private Long tempChatRoomId;

    /**
     * 怨듦컻諛⑷낵 鍮꾧났媛쒕갑 紐⑤몢?먯꽌 ?ъ슜?????덈뒗 珥덈? 肄붾뱶??
     */
    @Column(name = "invite_code", unique = true, length = 20)
    private String inviteCode;

    /**
     * Redis ???뷀듃由ъ? DB ?곹깭瑜??곌껐?섍린 ?꾪븳 異붿쟻 ?ㅻ떎.
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
            throw new IllegalArgumentException("? ?대쫫? 鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
        }
        if (status.isTerminal() || status.isQueueing()) {
            throw new IllegalStateException("?꾩옱 ?곹깭?먯꽌??? ?대쫫???섏젙?????놁뒿?덈떎.");
        }
        this.teamName = newTeamName.trim();
        touch();
    }

    public void addMember() {
        if (!status.canModifyMembers()) {
            throw new IllegalStateException("?꾩옱 ?곹깭?먯꽌????먯쓣 異붽??????놁뒿?덈떎.");
        }
        if (isFull()) {
            throw new IllegalStateException("? ?뺤썝??媛??李쇱뒿?덈떎.");
        }
        this.currentMemberCount++;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void removeMember() {
        if (!status.canModifyMembers()) {
            throw new IllegalStateException("?꾩옱 ?곹깭?먯꽌????먯쓣 ?쒓굅?????놁뒿?덈떎.");
        }
        if (currentMemberCount <= 1) {
            throw new IllegalStateException("??μ? ?쇱옄 ?⑤뜑?쇰룄 諛⑹씠 ?좎??섏뼱???섎?濡?1紐?誘몃쭔?쇰줈 以꾩씪 ???놁뒿?덈떎.");
        }
        this.currentMemberCount--;
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    public void enterReadyCheck(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal() || status.isQueueing()) {
            throw new IllegalStateException("?꾩옱 ?곹깭?먯꽌??以鍮??뺤씤 ?④퀎濡??꾪솚?????놁뒿?덈떎.");
        }
        if (!isFull()) {
            throw new IllegalStateException("?뺤썝??紐⑤몢 李⑥빞 以鍮??뺤씤???쒖옉?????덉뒿?덈떎.");
        }
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void reopenForRecruiting(Long requestUserId) {
        validateLeader(requestUserId);
        if (status == GTemporaryTeamRoomStatus.CLOSED || status == GTemporaryTeamRoomStatus.CANCELLED) {
            throw new IllegalStateException("醫낅즺??諛⑹? ?ㅼ떆 紐⑥쭛 ?곹깭濡??뚮┫ ???놁뒿?덈떎.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.OPEN;
        touch();
    }

    /**
     * ?꾩썝 以鍮??꾨즺 ?щ????쒕퉬??怨꾩링?먯꽌 寃利앺븯怨?
     * 洹?寃곌낵留?boolean 媛믪쑝濡??꾨떖諛쏆븘 ??吏꾩엯 ?щ?瑜??먮떒?쒕떎.
     */
    public void startQueue(Long requestUserId, boolean allMembersReady, String queueToken) {
        validateLeader(requestUserId);

        if (!status.canEnterQueue()) {
            throw new IllegalStateException("以鍮??뺤씤 ?곹깭?먯꽌留??먯뿉 吏꾩엯?????덉뒿?덈떎.");
        }
        if (!isFull()) {
            throw new IllegalStateException("?뺤썝??紐⑤몢 李⑥빞 ?먯뿉 吏꾩엯?????덉뒿?덈떎.");
        }
        if (!allMembersReady) {
            throw new IllegalStateException("????꾩썝??以鍮??꾨즺?ъ빞 ?⑸땲??");
        }
        if (queueToken == null || queueToken.isBlank()) {
            throw new IllegalArgumentException("queueToken? 鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
        }

        this.queueToken = queueToken;
        this.queuedAt = LocalDateTime.now();
        this.status = GTemporaryTeamRoomStatus.QUEUE_WAITING;
        touch();
    }

    public void leaveQueue() {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new IllegalStateException("???湲?以묒씤 ?留??먯뿉???섍컝 ???덉뒿?덈떎.");
        }
        clearQueueMetadata();
        this.status = GTemporaryTeamRoomStatus.READY_CHECK;
        touch();
    }

    public void markMatched() {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new IllegalStateException("???湲?以묒씤 ?留?留ㅼ묶 ?꾨즺 泥섎━?????덉뒿?덈떎.");
        }
        this.status = GTemporaryTeamRoomStatus.MATCHED;
        this.matchedAt = LocalDateTime.now();
        touch();
    }

    /**
     * Redis ?먭? ?좎떎?섏뿀????DB???⑥븘 ?덈뒗 ?湲??곹깭瑜?湲곗??쇰줈
     * ??硫뷀??곗씠?곕? 蹂듦뎄?쒕떎.
     */
    public boolean recoverQueueMetadata(String recoveredQueueToken) {
        if (this.status != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new IllegalStateException("???湲?以묒씤 ?留???硫뷀??곗씠?곕? 蹂듦뎄?????덉뒿?덈떎.");
        }
        if (recoveredQueueToken == null || recoveredQueueToken.isBlank()) {
            throw new IllegalArgumentException("recoveredQueueToken? 鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
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
            throw new IllegalStateException("留ㅼ묶 ?꾨즺???留?醫낅즺 泥섎━?????덉뒿?덈떎.");
        }
        this.status = GTemporaryTeamRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    public void cancel(Long requestUserId) {
        validateLeader(requestUserId);
        if (status.isTerminal()) {
            throw new IllegalStateException("?대? 醫낅즺???諛⑹엯?덈떎.");
        }
        this.status = GTemporaryTeamRoomStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        clearQueueMetadata();
        touch();
    }

    /**
     * ??먯씠 吏곸젒 ?ъ슜?????덈뒗 珥덈? 肄붾뱶瑜???ν븳??
     */
    public void assignInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("inviteCode??鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
        }
        if (!status.canModifyMembers()) {
            throw new IllegalStateException("?꾩옱 ?곹깭?먯꽌??珥덈? 肄붾뱶瑜?諛쒓툒?????놁뒿?덈떎.");
        }
        this.inviteCode = inviteCode.trim();
        touch();
    }

    /**
     * 媛숈? ? ?ш린, ?쒕줈 ?ㅻⅨ ? ?깅퀎, ?묒そ ?깅퀎 ?꾪꽣瑜?紐⑤몢 留뚯”?댁빞 留ㅼ묶?????덈떎.
     */
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
            throw new IllegalStateException("諛⑹옣留??섑뻾?????덉뒿?덈떎.");
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
            throw new IllegalArgumentException("leaderId???꾩닔?낅땲??");
        }
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("teamName? ?꾩닔?낅땲??");
        }
        if (teamGender == null) {
            throw new IllegalArgumentException("teamGender???꾩닔?낅땲??");
        }
        if (teamSize == null) {
            throw new IllegalArgumentException("teamSize???꾩닔?낅땲??");
        }
        if (opponentGenderFilter == null) {
            throw new IllegalArgumentException("opponentGenderFilter???꾩닔?낅땲??");
        }
        if (visibility == null) {
            throw new IllegalArgumentException("visibility???꾩닔?낅땲??");
        }
        if (tempChatRoomId == null) {
            throw new IllegalArgumentException("tempChatRoomId???꾩닔?낅땲??");
        }
    }
}

