package univ.airconnect.groupmatching.dto.response;

import univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.service.GMatchingService;

import java.time.LocalDateTime;
import java.util.List;

public final class GMatchingResponse {

    private GMatchingResponse() {
    }

    public record PublicTeamRoomSummaryResponse(
            Long id,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            int targetMemberCount,
            int currentMemberCount,
            GTemporaryTeamRoomStatus status,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Long tempChatRoomId,
            LocalDateTime createdAt
    ) {
        public static PublicTeamRoomSummaryResponse from(GTemporaryTeamRoom room) {
            return new PublicTeamRoomSummaryResponse(
                    room.getId(),
                    room.getTeamName(),
                    room.getTeamGender(),
                    room.getTeamSize(),
                    room.getTeamSize().getValue(),
                    room.getCurrentMemberCount(),
                    room.getStatus(),
                    room.getOpponentGenderFilter(),
                    room.getVisibility(),
                    room.getTempChatRoomId(),
                    room.getCreatedAt()
            );
        }
    }

    public record TeamMemberSummaryResponse(
            Long userId,
            String nickname,
            boolean leader,
            boolean active,
            boolean ready,
            LocalDateTime joinedAt,
            LocalDateTime leftAt
    ) {
        public static TeamMemberSummaryResponse from(
                GTemporaryTeamMember member,
                String nickname,
                boolean ready
        ) {
            return new TeamMemberSummaryResponse(
                    member.getUserId(),
                    nickname,
                    member.isLeader(),
                    member.isActiveMember(),
                    ready,
                    member.getJoinedAt(),
                    member.getLeftAt()
            );
        }
    }

    public record TemporaryTeamRoomResponse(
            Long id,
            Long leaderId,
            boolean meLeader,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            int targetMemberCount,
            int currentMemberCount,
            boolean full,
            GTemporaryTeamRoomStatus status,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Long tempChatRoomId,
            String inviteCode,
            int readyMemberCount,
            boolean allMembersReady,
            boolean canStartMatching,
            String queueToken,
            LocalDateTime queuedAt,
            LocalDateTime matchedAt,
            LocalDateTime closedAt,
            LocalDateTime cancelledAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TeamMemberSummaryResponse> members
    ) {
        public static TemporaryTeamRoomResponse of(
                GTemporaryTeamRoom room,
                boolean meLeader,
                int readyMemberCount,
                boolean allMembersReady,
                boolean canStartMatching,
                List<TeamMemberSummaryResponse> members
        ) {
            return new TemporaryTeamRoomResponse(
                    room.getId(),
                    room.getLeaderId(),
                    meLeader,
                    room.getTeamName(),
                    room.getTeamGender(),
                    room.getTeamSize(),
                    room.getTeamSize().getValue(),
                    room.getCurrentMemberCount(),
                    room.isFull(),
                    room.getStatus(),
                    room.getOpponentGenderFilter(),
                    room.getVisibility(),
                    room.getTempChatRoomId(),
                    room.getInviteCode(),
                    readyMemberCount,
                    allMembersReady,
                    canStartMatching,
                    room.getQueueToken(),
                    room.getQueuedAt(),
                    room.getMatchedAt(),
                    room.getClosedAt(),
                    room.getCancelledAt(),
                    room.getCreatedAt(),
                    room.getUpdatedAt(),
                    members
            );
        }
    }

    public record QueueSnapshotResponse(
            Long teamRoomId,
            String status,
            int position,
            int aheadCount,
            int totalWaitingTeams,
            Long finalGroupRoomId,
            Long finalChatRoomId,
            boolean matched
    ) {
        public static QueueSnapshotResponse from(GMatchingService.QueueSnapshot snapshot) {
            boolean matched = snapshot.finalGroupRoomId() != null && snapshot.finalChatRoomId() != null;
            return new QueueSnapshotResponse(
                    snapshot.teamRoomId(),
                    snapshot.status(),
                    snapshot.position(),
                    snapshot.aheadCount(),
                    snapshot.totalWaitingTeams(),
                    snapshot.finalGroupRoomId(),
                    snapshot.finalChatRoomId(),
                    matched
            );
        }
    }

    public record FinalGroupChatRoomResponse(
            Long id,
            Long chatRoomId,
            Long team1RoomId,
            Long team2RoomId,
            Long matchResultId,
            GTeamSize teamSize,
            int finalMemberCount,
            GFinalGroupRoomStatus status,
            LocalDateTime createdAt,
            LocalDateTime endedAt,
            LocalDateTime cancelledAt,
            LocalDateTime updatedAt
    ) {
        public static FinalGroupChatRoomResponse from(GFinalGroupChatRoom room) {
            return new FinalGroupChatRoomResponse(
                    room.getId(),
                    room.getChatRoomId(),
                    room.getTeam1RoomId(),
                    room.getTeam2RoomId(),
                    room.getMatchResultId(),
                    room.getTeamSize(),
                    room.getFinalMemberCount(),
                    room.getStatus(),
                    room.getCreatedAt(),
                    room.getEndedAt(),
                    room.getCancelledAt(),
                    room.getUpdatedAt()
            );
        }
    }

    public record MyMatchingStateResponse(
            String state,
            TemporaryTeamRoomResponse teamRoom,
            QueueSnapshotResponse queueSnapshot,
            FinalGroupChatRoomResponse finalRoom,
            String matchingSubscriptionDestination
    ) {
        public static MyMatchingStateResponse idle() {
            return new MyMatchingStateResponse("IDLE", null, null, null, null);
        }

        public static MyMatchingStateResponse inTemporaryTeamRoom(
                TemporaryTeamRoomResponse teamRoom,
                QueueSnapshotResponse queueSnapshot,
                String matchingSubscriptionDestination
        ) {
            return new MyMatchingStateResponse(
                    "TEMPORARY_TEAM_ROOM",
                    teamRoom,
                    queueSnapshot,
                    null,
                    matchingSubscriptionDestination
            );
        }

        public static MyMatchingStateResponse inFinalGroupRoom(FinalGroupChatRoomResponse finalRoom) {
            return new MyMatchingStateResponse("FINAL_GROUP_CHAT_ROOM", null, null, finalRoom, null);
        }
    }
}
