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
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

import java.security.SecureRandom;
import java.util.Locale;
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
    private static final long CODE_EXPIRATION_MINUTES = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final String SCHOOL_DOMAIN = "office.hanseo.ac.kr";
    private static final SecureRandom RANDOM = new SecureRandom();

    public void sendCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateEmailDomain(normalizedEmail);
        checkResendCooldown(normalizedEmail);

        String code = generateCode();
        saveCode(normalizedEmail, code);
        saveCooldown(normalizedEmail);

        try {
            mailService.sendVerificationCode(
                    normalizedEmail,
                    code,
                    CODE_EXPIRATION_MINUTES,
                    SCHOOL_DOMAIN
            );
            log.info("Verification code issued. email={}", normalizedEmail);
        } catch (VerificationException e) {
            clearVerificationData(normalizedEmail);
            throw e;
        } catch (Exception e) {
            clearVerificationData(normalizedEmail);
            log.error("Unexpected error while sending verification code. email={}", normalizedEmail, e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
    }

    @Transactional
    public void verifyCode(Long userId, String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        validateEmailDomain(normalizedEmail);

        String savedCode = redisTemplate.opsForValue().get(VERIFICATION_PREFIX + normalizedEmail);
        if (savedCode == null) {
            throw new VerificationException(VerificationErrorCode.CODE_EXPIRED);
        }
        if (!savedCode.equals(code.trim())) {
            throw new VerificationException(VerificationErrorCode.CODE_MISMATCH);
        }

        clearVerificationData(normalizedEmail);
        grantMilestoneIfNotAlreadyGranted(userId, normalizedEmail);

        log.info("Email verification succeeded. userId={}, email={}", userId, normalizedEmail);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new VerificationException(VerificationErrorCode.INVALID_EMAIL_FORMAT);
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.contains("@")) {
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

    private void grantMilestoneIfNotAlreadyGranted(Long userId, String verifiedEmail) {
        if (userId == null) {
            log.warn("Email verified without authenticated user. milestone skipped. email={}", verifiedEmail);
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
                verifiedEmail,
                rewardTickets,
                user.getTickets()
        );
    }
}
