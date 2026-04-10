package univ.airconnect.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.infrastructure.ReviewAccountProperties;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAccountBootstrapService {

    private final ReviewAccountService reviewAccountService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureReviewAccount() {
        if (!reviewAccountService.isEnabledAndConfigured()) {
            return;
        }

        ReviewAccountProperties props = reviewAccountService.properties();
        String email = reviewAccountService.normalizedEmail();

        User user = userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, email)
                .orElseGet(() -> userRepository.save(
                        User.createEmailUser(email, passwordEncoder.encode(reviewAccountService.rawPassword()))
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Review account exists but is not active. emailMasked={}", maskEmail(email));
            return;
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(reviewAccountService.rawPassword(), user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(reviewAccountService.rawPassword()));
        }

        user.completeSignUp(
                props.resolvedName(),
                props.resolvedNickname(),
                props.resolvedStudentNum(),
                props.resolvedDeptName()
        );

        userProfileRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        profile -> profile.update(
                                props.resolvedHeight(),
                                props.resolvedAge(),
                                props.resolvedMbti(),
                                props.resolvedSmoking(),
                                props.resolvedGender(),
                                props.resolvedMilitary(),
                                props.resolvedReligion(),
                                props.resolvedResidence(),
                                props.intro(),
                                props.instagram()
                        ),
                        () -> userProfileRepository.save(
                                UserProfile.create(
                                        user,
                                        props.resolvedHeight(),
                                        props.resolvedAge(),
                                        props.resolvedMbti(),
                                        props.resolvedSmoking(),
                                        props.resolvedGender(),
                                        props.resolvedMilitary(),
                                        props.resolvedReligion(),
                                        props.resolvedResidence(),
                                        props.intro(),
                                        props.instagram()
                                )
                        )
                );

        if (!userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(user.getId(), MilestoneType.EMAIL_VERIFIED)) {
            userMilestoneRepository.save(UserMilestone.create(user.getId(), MilestoneType.EMAIL_VERIFIED));
        }

        log.info("Review account is ready. emailMasked={}, userId={}", maskEmail(email), user.getId());
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
