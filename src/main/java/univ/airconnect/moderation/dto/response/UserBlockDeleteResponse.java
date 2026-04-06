package univ.airconnect.moderation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserBlockDeleteResponse {
    private Long blockerUserId;
    private Long blockedUserId;
    private boolean removed;
}
