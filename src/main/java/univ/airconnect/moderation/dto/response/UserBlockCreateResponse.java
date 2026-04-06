package univ.airconnect.moderation.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.moderation.domain.entity.UserBlock;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class UserBlockCreateResponse {

    private Long blockerUserId;
    private Long blockedUserId;
    private OffsetDateTime blockedAt;
    private boolean alreadyBlocked;

    public static UserBlockCreateResponse created(UserBlock userBlock) {
        return UserBlockCreateResponse.builder()
                .blockerUserId(userBlock.getBlockerUserId())
                .blockedUserId(userBlock.getBlockedUserId())
                .blockedAt(userBlock.getCreatedAt().atOffset(ZoneOffset.UTC))
                .alreadyBlocked(false)
                .build();
    }

    public static UserBlockCreateResponse alreadyExists(UserBlock userBlock) {
        return UserBlockCreateResponse.builder()
                .blockerUserId(userBlock.getBlockerUserId())
                .blockedUserId(userBlock.getBlockedUserId())
                .blockedAt(userBlock.getCreatedAt().atOffset(ZoneOffset.UTC))
                .alreadyBlocked(true)
                .build();
    }
}
