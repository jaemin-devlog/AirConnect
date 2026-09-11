package univ.airconnect.global.security.stomp;

import java.time.LocalDateTime;

public record OnlineUserCountChangedEvent(
        int onlineUserCount,
        LocalDateTime occurredAt
) {
    public static OnlineUserCountChangedEvent of(int onlineUserCount) {
        return new OnlineUserCountChangedEvent(onlineUserCount, LocalDateTime.now());
    }
}
