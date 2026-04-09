package univ.airconnect.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SchoolConsentUpsertRequest {

    @NotNull(message = "성인 및 대학 인증 가능 사용자 확인 동의는 필수입니다.")
    private Boolean adultAndCollegeConfirmed;

    @NotNull(message = "이용약관 동의는 필수입니다.")
    private Boolean termsOfServiceAgreed;

    @NotNull(message = "개인정보 수집 및 이용 동의는 필수입니다.")
    private Boolean privacyCollectionAgreed;

    @NotNull(message = "프로필 정보 제공 동의는 필수입니다.")
    private Boolean profileDisclosureAgreed;

    @NotNull(message = "마케팅 수신 동의 값은 필수입니다. (true/false)")
    private Boolean marketingAgreed;
}

