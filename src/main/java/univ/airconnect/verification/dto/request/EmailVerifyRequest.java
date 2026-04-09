package univ.airconnect.verification.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.verification.domain.VerificationPurpose;

@Getter
@NoArgsConstructor
public class EmailVerifyRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "인증 코드는 필수입니다.")
    private String code;

    private VerificationPurpose purpose;
}
