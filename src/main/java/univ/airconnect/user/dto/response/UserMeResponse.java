package univ.airconnect.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.UserStatus;

@Getter
@Builder
public class UserMeResponse {

    private Long userId;
    private SocialProvider provider;
    private String socialId;
    private UserStatus status;
}