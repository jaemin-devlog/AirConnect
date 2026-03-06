package univ.airconnect.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AppleLoginRequest {

    private String identityToken;
    private String authorizationCode;

}