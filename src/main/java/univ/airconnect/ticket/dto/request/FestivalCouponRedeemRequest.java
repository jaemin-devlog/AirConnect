package univ.airconnect.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FestivalCouponRedeemRequest(
        @NotBlank(message = "쿠폰 코드를 입력해 주세요.")
        @Pattern(regexp = "\\d{6}", message = "쿠폰 코드는 숫자 6자리여야 합니다.")
        String code
) {
}
