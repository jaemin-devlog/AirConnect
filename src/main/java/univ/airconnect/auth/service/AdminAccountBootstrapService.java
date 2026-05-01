package univ.airconnect.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.infrastructure.AdminAccountProperties;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountBootstrapService {

    private final AdminAccountService adminAccountService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureAdminAccount() {
        if (!adminAccountService.isEnabledAndConfigured()) {
            return;
        }

        AdminAccountProperties props = adminAccountService.properties();
        String email = adminAccountService.normalizedEmail();

        User user = userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, email)
                .orElseGet(() -> userRepository.save(
                        User.createEmailUser(email, passwordEncoder.encode(adminAccountService.rawPassword()))
                ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Admin account exists but is not active. emailMasked={}", maskEmail(email));
            return;
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(adminAccountService.rawPassword(), user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(adminAccountService.rawPassword()));
        }

        if (!user.isAdmin()) {
            user.changeRole(UserRole.ADMIN);
        }

        if (needsBasicInfoSync(user, props)) {
            user.completeSignUp(
                    props.resolvedName(),
                    props.resolvedNickname(),
                    props.resolvedStudentNum(),
                    props.resolvedDeptName()
            );
        }

        log.info("Admin account is ready. emailMasked={}, userId={}", maskEmail(email), user.getId());
    }

    private boolean needsBasicInfoSync(User user, AdminAccountProperties props) {
        return !props.resolvedName().equals(user.getName())
                || !props.resolvedNickname().equals(user.getNickname())
                || !props.resolvedDeptName().equals(user.getDeptName())
                || !props.resolvedStudentNum().equals(user.getStudentNum());
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
