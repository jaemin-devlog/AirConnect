package univ.airconnect.verification.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class EmailVerifyResponse {

    private String verifiedEmail;
    private String verificationToken;
    private OffsetDateTime expiresAt;
}

