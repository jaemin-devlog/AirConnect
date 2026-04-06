package univ.airconnect.moderation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class UserBlockStatusResponse {
    private Long blockerUserId;
    private Long blockedUserId;
    private boolean blocked;
    private OffsetDateTime blockedAt;
}
