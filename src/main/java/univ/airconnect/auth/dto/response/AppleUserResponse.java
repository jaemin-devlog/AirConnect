package univ.airconnect.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AppleUserResponse {

    private String appleId;
    private String email;

}