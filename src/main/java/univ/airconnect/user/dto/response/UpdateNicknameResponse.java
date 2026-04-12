package univ.airconnect.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateNicknameResponse {

    private Long userId;
    private String nickname;
}
