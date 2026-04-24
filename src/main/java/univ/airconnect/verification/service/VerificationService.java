package univ.airconnect.verification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.verification.domain.VerificationPurpose;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final MailService mailService;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final MilestoneRewardProperties milestoneRewardProperties;

    private static final String VERIFICATION_PREFIX = "email_verification:";
    private static final String COOLDOWN_PREFIX = "email_verification_cooldown:";
    private static final String VERIFIED_TOKEN_PREFIX = "email_verified_token:";
    private static final String VERIFIED_ACTIVE_PREFIX = "email_verified_active:";
    private static final long CODE_EXPIRATION_MINUTES = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final long VERIFIED_TOKEN_EXPIRATION_MINUTES = 20;
    private static final String SCHOOL_DOMAIN = "office.hanseo.ac.kr";
    private static final SecureRandom RANDOM = new SecureRandom();

    public void sendCode(String email, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        validateEmailDomain(normalizedEmail);
        VerificationPurpose resolvedPurpose = resolvePurpose(normalizedEmail, purpose);
        assertSignUpEmailAvailability(normalizedEmail, resolvedPurpose);
        clearActiveVerifiedSession(normalizedEmail);
        checkResendCooldown(normalizedEmail);

        String code = generateCode();
        saveCode(normalizedEmail, code);
        saveCooldown(normalizedEmail);

        try {
            mailService.sendVerificationCode(normalizedEmail, code);
            log.info("Verification code issued. email={}", maskEmail(normalizedEmail));
        } catch (VerificationException e) {
            clearVerificationData(normalizedEmail);
            throw e;
        } catch (Exception e) {
            clearVerificationData(normalizedEmail);
            log.error("Unexpected error while sending verification code. email={}", maskEmail(normalizedEmail), e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
    }

    @Transactional
    public VerifiedEmailSession verifyCode(Long userId, String email, String code, VerificationPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        validateEmailDomain(normalizedEmail);
        VerificationPurpose resolvedPurpose = resolvePurpose(normalizedEmail, purpose);
        assertSignUpEmailAvailability(normalizedEmail, resolvedPurpose);

        String savedCode = redisTemplate.opsForValue().get(VERIFICATION_PREFIX + normalizedEmail);
        if (savedCode == null) {
            throw new VerificationException(VerificationErrorCode.CODE_EXPIRED);
        }
        if (!savedCode.equals(code.trim())) {
            throw new VerificationException(VerificationErrorCode.CODE_MISMATCH);
        }

        clearVerificationData(normalizedEmail);
        clearActiveVerifiedSession(normalizedEmail);
        linkVerifiedEmailToUser(userId, normalizedEmail);
        grantMilestoneIfNotAlreadyGranted(userId, normalizedEmail);
        VerifiedEmailSession verifiedSession = issueVerifiedSession(normalizedEmail);

        log.info("Email verification succeeded. userId={}, email={}, tokenMasked={}",
                userId, maskEmail(normalizedEmail), maskToken(verifiedSession.verificationToken()));
        return verifiedSession;
    }

    public String resolveVerifiedEmail(String verificationToken) {
        String token = normalizeVerificationToken(verificationToken);
        String email = redisTemplate.opsForValue().get(VERIFIED_TOKEN_PREFIX + token);
        if (email == null || email.isBlank()) {
            throw new VerificationException(VerificationErrorCode.VERIFIED_EMAIL_TOKEN_EXPIRED);
        }
        return email;
    }

    public String consumeVerifiedEmail(String verificationToken) {
        String token = normalizeVerificationToken(verificationToken);
        String key = VERIFIED_TOKEN_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        if (email == null || email.isBlank()) {
            throw new VerificationException(VerificationErrorCode.VERIFIED_EMAIL_TOKEN_EXPIRED);
        }
        redisTemplate.delete(key);
        redisTemplate.delete(VERIFIED_ACTIVE_PREFIX + email);
        return email;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_FORMAT);
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_FORMAT);
        }
        if (normalized.length() > 100) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_FORMAT);
        }
        return normalized;
    }

    private void validateEmailDomain(String email) {
        int atIndex = email.lastIndexOf("@");
        if (atIndex < 0 || atIndex == email.length() - 1) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_FORMAT);
        }

        String domain = email.substring(atIndex + 1);
        if (!SCHOOL_DOMAIN.equals(domain)) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_DOMAIN);
        }
    }

    private void checkResendCooldown(String email) {
        Boolean exists = redisTemplate.hasKey(COOLDOWN_PREFIX + email);
        if (Boolean.TRUE.equals(exists)) {
            throw new VerificationException(VerificationErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private String generateCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private void saveCode(String email, String code) {
        redisTemplate.opsForValue().set(
                VERIFICATION_PREFIX + email,
                code,
                CODE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private void saveCooldown(String email) {
        redisTemplate.opsForValue().set(
                COOLDOWN_PREFIX + email,
                "1",
                RESEND_COOLDOWN_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void clearVerificationData(String email) {
        redisTemplate.delete(VERIFICATION_PREFIX + email);
        redisTemplate.delete(COOLDOWN_PREFIX + email);
    }

    private VerifiedEmailSession issueVerifiedSession(String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                VERIFIED_TOKEN_PREFIX + token,
                email,
                VERIFIED_TOKEN_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                VERIFIED_ACTIVE_PREFIX + email,
                token,
                VERIFIED_TOKEN_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );
        return new VerifiedEmailSession(
                email,
                token,
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(VERIFIED_TOKEN_EXPIRATION_MINUTES)
        );
    }

    private void clearActiveVerifiedSession(String email) {
        String activeToken = redisTemplate.opsForValue().get(VERIFIED_ACTIVE_PREFIX + email);
        if (activeToken == null || activeToken.isBlank()) {
            return;
        }

        redisTemplate.delete(VERIFIED_ACTIVE_PREFIX + email);
        redisTemplate.delete(VERIFIED_TOKEN_PREFIX + activeToken);
    }

    private void assertSignUpEmailAvailability(String normalizedEmail, VerificationPurpose purpose) {
        if (purpose != VerificationPurpose.SIGN_UP) {
            return;
        }
        boolean existsEmailAccount = userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, normalizedEmail)
                .isPresent();
        boolean linkedToAnyUser = userRepository.existsByEmailIgnoreCase(normalizedEmail)
                || userRepository.existsByVerifiedSchoolEmailIgnoreCase(normalizedEmail);
        if (existsEmailAccount || linkedToAnyUser) {
            throw new VerificationException(VerificationErrorCode.ALREADY_REGISTERED_EMAIL);
        }
    }

    private VerificationPurpose resolvePurpose(String normalizedEmail, VerificationPurpose purpose) {
        if (purpose != null) {
            return purpose;
        }

        boolean existsEmailAccount = userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, normalizedEmail)
                .isPresent();
        return existsEmailAccount ? VerificationPurpose.LOGIN : VerificationPurpose.SIGN_UP;
    }

    private String normalizeVerificationToken(String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new VerificationException(VerificationErrorCode.VERIFIED_EMAIL_TOKEN_REQUIRED);
        }
        return verificationToken.trim();
    }

    private void grantMilestoneIfNotAlreadyGranted(Long userId, String verifiedEmail) {
        if (userId == null) {
            log.info("Email verified without authenticated user. milestone skipped. email={}", maskEmail(verifiedEmail));
            return;
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        boolean alreadyGranted = userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(
                userId,
                MilestoneType.EMAIL_VERIFIED
        );
        if (alreadyGranted) {
            log.info("Milestone already granted. userId={}, milestoneType=EMAIL_VERIFIED", userId);
            return;
        }

        UserMilestone milestone = UserMilestone.create(userId, MilestoneType.EMAIL_VERIFIED);
        try {
            userMilestoneRepository.save(milestone);
        } catch (DataIntegrityViolationException e) {
            log.info("Milestone already granted by concurrent request. userId={}, milestoneType=EMAIL_VERIFIED", userId);
            return;
        }

        int rewardTickets = Math.max(0, milestoneRewardProperties.getEmailVerifiedTickets());
        if (rewardTickets > 0) {
            user.addTickets(rewardTickets);
        }
        log.info(
                "Milestone granted. userId={}, verifiedEmail={}, milestoneType=EMAIL_VERIFIED, rewardedTickets={}, totalTickets={}",
                userId,
                maskEmail(verifiedEmail),
                rewardTickets,
                user.getTickets()
        );
    }

    private void linkVerifiedEmailToUser(Long userId, String verifiedEmail) {
        if (userId == null) {
            return;
        }

        userRepository.findById(userId)
                .ifPresent(user -> user.updateVerifiedSchoolEmail(verifiedEmail));
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

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "***";
        }
        if (token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
