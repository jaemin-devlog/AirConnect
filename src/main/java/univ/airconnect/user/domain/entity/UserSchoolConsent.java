package univ.airconnect.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_school_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSchoolConsent {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "adult_and_college_confirmed", nullable = false)
    private Boolean adultAndCollegeConfirmed;

    @Column(name = "terms_of_service_agreed", nullable = false)
    private Boolean termsOfServiceAgreed;

    @Column(name = "privacy_collection_agreed", nullable = false)
    private Boolean privacyCollectionAgreed;

    @Column(name = "profile_disclosure_agreed", nullable = false)
    private Boolean profileDisclosureAgreed;

    @Column(name = "marketing_agreed", nullable = false)
    private Boolean marketingAgreed;

    @Column(name = "required_consents_agreed", nullable = false)
    private Boolean requiredConsentsAgreed;

    @Column(name = "all_consents_agreed", nullable = false)
    private Boolean allConsentsAgreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserSchoolConsent(Long userId,
                              boolean adultAndCollegeConfirmed,
                              boolean termsOfServiceAgreed,
                              boolean privacyCollectionAgreed,
                              boolean profileDisclosureAgreed,
                              boolean marketingAgreed,
                              LocalDateTime agreedAt) {
        this.userId = userId;
        applyConsentValues(
                adultAndCollegeConfirmed,
                termsOfServiceAgreed,
                privacyCollectionAgreed,
                profileDisclosureAgreed,
                marketingAgreed,
                agreedAt
        );
    }

    public static UserSchoolConsent create(Long userId,
                                           boolean adultAndCollegeConfirmed,
                                           boolean termsOfServiceAgreed,
                                           boolean privacyCollectionAgreed,
                                           boolean profileDisclosureAgreed,
                                           boolean marketingAgreed) {
        LocalDateTime now = LocalDateTime.now();
        return new UserSchoolConsent(
                userId,
                adultAndCollegeConfirmed,
                termsOfServiceAgreed,
                privacyCollectionAgreed,
                profileDisclosureAgreed,
                marketingAgreed,
                now
        );
    }

    public void update(boolean adultAndCollegeConfirmed,
                       boolean termsOfServiceAgreed,
                       boolean privacyCollectionAgreed,
                       boolean profileDisclosureAgreed,
                       boolean marketingAgreed) {
        applyConsentValues(
                adultAndCollegeConfirmed,
                termsOfServiceAgreed,
                privacyCollectionAgreed,
                profileDisclosureAgreed,
                marketingAgreed,
                LocalDateTime.now()
        );
    }

    private void applyConsentValues(boolean adultAndCollegeConfirmed,
                                    boolean termsOfServiceAgreed,
                                    boolean privacyCollectionAgreed,
                                    boolean profileDisclosureAgreed,
                                    boolean marketingAgreed,
                                    LocalDateTime now) {
        this.adultAndCollegeConfirmed = adultAndCollegeConfirmed;
        this.termsOfServiceAgreed = termsOfServiceAgreed;
        this.privacyCollectionAgreed = privacyCollectionAgreed;
        this.profileDisclosureAgreed = profileDisclosureAgreed;
        this.marketingAgreed = marketingAgreed;

        boolean requiredAgreed = adultAndCollegeConfirmed
                && termsOfServiceAgreed
                && privacyCollectionAgreed
                && profileDisclosureAgreed;
        this.requiredConsentsAgreed = requiredAgreed;
        this.allConsentsAgreed = requiredAgreed && marketingAgreed;

        this.agreedAt = now;
        this.updatedAt = now;
    }
}

