package univ.airconnect.statistics.dto.response;

import java.time.LocalDateTime;

public record OnlinePresenceResponse(
        int onlineUserCount,
        LocalDateTime updatedAt
) {
    public static OnlinePresenceResponse of(int onlineUserCount) {
        return new OnlinePresenceResponse(onlineUserCount, LocalDateTime.now());
    }
}
