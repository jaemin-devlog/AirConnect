package univ.airconnect.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminGroupMatchingDtos {
    private AdminGroupMatchingDtos() {}

    public record Team(Long teamId, String teamName, Long leaderId, int teamSize, String teamGender, String status,
                       int storedMemberCount, LocalDateTime createdAt, LocalDateTime queuedAt,
                       LocalDateTime matchedAt, LocalDateTime closedAt, LocalDateTime cancelledAt) {}
    public record Participant(Long userId, String nickname, boolean userRecordPresent, boolean leader,
                              boolean active, Boolean ready, LocalDateTime joinedAt,
                              LocalDateTime leftAt, LocalDateTime expelledAt) {}
    public record Room(Long chatRoomId, boolean exists, boolean group, Long membershipCount) {}
    public record Match(Long resultId, String status, Long opponentTeamId, String opponentTeamName,
                        boolean opponentExists, String opponentStatus, Long opponentActiveMemberCount,
                        Long finalGroupRoomId, LocalDateTime matchedAt,
                        LocalDateTime finalRoomCreatedAt) {}
    public record FinalRoom(Long finalGroupRoomId, Long resultId, Long team1Id, Long team2Id,
                            int teamSize, String status, Room chatRoom, LocalDateTime createdAt) {}
    public record Queue(String presence, Long length, int sampledCount, boolean truncated,
                        Integer firstPosition, int occurrences, int invalidCount, Instant observedAt) {}
    public record WaitingTeam(Team team, long activeMemberCount, Long elapsedWaitSeconds, Queue queue) {}
    public record WaitingBoard(int teamSize, AdminDtos.PageResponse<WaitingTeam> male,
                               AdminDtos.PageResponse<WaitingTeam> female, Instant observedAt) {}
    public record Detail(Team team, String stage, List<String> observations, List<Participant> participants,
                         int activeMemberCount, int readyMemberCount, Room temporaryRoom,
                         List<Match> matches, List<FinalRoom> finalRooms, boolean historyTruncated,
                         Queue queue, Instant observedAt) {}
}
