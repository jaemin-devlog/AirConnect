package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserSchoolConsent;
import univ.airconnect.user.dto.request.SchoolConsentUpsertRequest;
import univ.airconnect.user.dto.response.SchoolConsentResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserSchoolConsentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSchoolConsentService {

    private final UserRepository userRepository;
    private final UserSchoolConsentRepository userSchoolConsentRepository;

    public SchoolConsentResponse get(Long userId) {
        ensureUserActive(userId);
        UserSchoolConsent consent = userSchoolConsentRepository.findById(userId).orElse(null);
        return SchoolConsentResponse.from(userId, consent);
    }

    @Transactional
    public SchoolConsentResponse upsert(Long userId, SchoolConsentUpsertRequest request) {
        ensureUserActive(userId);
        if (request == null) {
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        boolean adultAndCollegeConfirmed = Boolean.TRUE.equals(request.getAdultAndCollegeConfirmed());
        boolean termsOfServiceAgreed = Boolean.TRUE.equals(request.getTermsOfServiceAgreed());
        boolean privacyCollectionAgreed = Boolean.TRUE.equals(request.getPrivacyCollectionAgreed());
        boolean profileDisclosureAgreed = Boolean.TRUE.equals(request.getProfileDisclosureAgreed());
        boolean marketingAgreed = Boolean.TRUE.equals(request.getMarketingAgreed());

        if (!adultAndCollegeConfirmed || !termsOfServiceAgreed || !privacyCollectionAgreed || !profileDisclosureAgreed) {
            throw new UserException(UserErrorCode.REQUIRED_CONSENT_NOT_ACCEPTED);
        }

        UserSchoolConsent consent = userSchoolConsentRepository.findById(userId)
                .map(existing -> {
                    existing.update(
                            adultAndCollegeConfirmed,
                            termsOfServiceAgreed,
                            privacyCollectionAgreed,
                            profileDisclosureAgreed,
                            marketingAgreed
                    );
                    return existing;
                })
                .orElseGet(() -> UserSchoolConsent.create(
                        userId,
                        adultAndCollegeConfirmed,
                        termsOfServiceAgreed,
                        privacyCollectionAgreed,
                        profileDisclosureAgreed,
                        marketingAgreed
                ));

        UserSchoolConsent saved = userSchoolConsentRepository.save(consent);
        return SchoolConsentResponse.from(userId, saved);
    }

    private void ensureUserActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new UserException(UserErrorCode.USER_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UserException(UserErrorCode.USER_SUSPENDED);
        }
    }
}

