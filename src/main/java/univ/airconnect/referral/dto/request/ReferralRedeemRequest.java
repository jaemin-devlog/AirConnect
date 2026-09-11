package univ.airconnect.referral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReferralRedeemRequest(
        @NotBlank(message = "추천인 코드를 입력해 주세요.")
        @Pattern(
                regexp = "(?i)(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{6}",
                message = "추천인 코드는 영문과 숫자로 이루어진 6자리여야 합니다."
        )
        String referralCode
) {
}
