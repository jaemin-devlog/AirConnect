package univ.airconnect.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.entity.UserSchoolConsent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class SchoolConsentResponse {

    private Long userId;
    private Boolean adultAndCollegeConfirmed;
    private Boolean termsOfServiceAgreed;
    private Boolean privacyCollectionAgreed;
    private Boolean profileDisclosureAgreed;
    private Boolean marketingAgreed;
    private Boolean requiredConsentsAgreed;
    private Boolean allConsentsAgreed;
    private Boolean canProceedSchoolVerification;
    private OffsetDateTime agreedAt;
    private OffsetDateTime updatedAt;

    public static SchoolConsentResponse from(Long userId, UserSchoolConsent consent) {
        if (consent == null) {
            return SchoolConsentResponse.builder()
                    .userId(userId)
                    .adultAndCollegeConfirmed(false)
                    .termsOfServiceAgreed(false)
                    .privacyCollectionAgreed(false)
                    .profileDisclosureAgreed(false)
                    .marketingAgreed(false)
                    .requiredConsentsAgreed(false)
                    .allConsentsAgreed(false)
                    .canProceedSchoolVerification(false)
                    .agreedAt(null)
                    .updatedAt(null)
                    .build();
        }

        return SchoolConsentResponse.builder()
                .userId(consent.getUserId())
                .adultAndCollegeConfirmed(consent.getAdultAndCollegeConfirmed())
                .termsOfServiceAgreed(consent.getTermsOfServiceAgreed())
                .privacyCollectionAgreed(consent.getPrivacyCollectionAgreed())
                .profileDisclosureAgreed(consent.getProfileDisclosureAgreed())
                .marketingAgreed(consent.getMarketingAgreed())
                .requiredConsentsAgreed(consent.getRequiredConsentsAgreed())
                .allConsentsAgreed(consent.getAllConsentsAgreed())
                .canProceedSchoolVerification(consent.getRequiredConsentsAgreed())
                .agreedAt(consent.getAgreedAt() != null ? consent.getAgreedAt().atOffset(ZoneOffset.UTC) : null)
                .updatedAt(consent.getUpdatedAt() != null ? consent.getUpdatedAt().atOffset(ZoneOffset.UTC) : null)
                .build();
    }
}

