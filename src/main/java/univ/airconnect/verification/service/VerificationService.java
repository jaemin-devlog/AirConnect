package univ.airconnect.verification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
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
            mailService.sendVerificationCode(normalizedEmail, code);
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

    public void verifyCode(String email, String code) {
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

        // 마일리스톤 부여 (email 기반 사용자 찾아서 처리)
        grantMilestoneIfNotAlreadyGranted(normalizedEmail);

        log.info("Email verification succeeded. email={}", normalizedEmail);
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

    /**
     * 이메일 인증 마일리스톤을 부여합니다. 과거에 같은 마일리스톤이 부여된 적이 없으면 티켓 1개를 추가하고 기록합니다.
     *
     * @param email 사용자 이메일
     */
    private void grantMilestoneIfNotAlreadyGranted(String email) {
        var user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            log.warn("⚠️ 이메일에 해당하는 사용자를 찾을 수 없음 (마일리스톤 미부여): email={}", email);
            return;
        }

        // 이미 이 마일리스톤이 부여되었고 granted = true인지 확인
        boolean alreadyGranted = userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(
                user.getId(), 
                MilestoneType.EMAIL_VERIFIED
        );

        if (alreadyGranted) {
            log.info("ℹ️ 이미 지급된 마일리스톤 (중복 부여 방지): userId={}, email={}, milestoneType=EMAIL_VERIFIED", 
                    user.getId(), email);
            return;
        }

        // 마일리스톤 기록 추가
        UserMilestone milestone = UserMilestone.create(user.getId(), MilestoneType.EMAIL_VERIFIED);
        userMilestoneRepository.save(milestone);

        // 사용자 티켓 1개 추가
        user.addTickets(1);

        log.info("🎫 마일리스톤 지급 완료: userId={}, email={}, milestoneType=EMAIL_VERIFIED, 부여 티켓=1, 총 티켓={}", 
                user.getId(), email, user.getTickets());
    }
}