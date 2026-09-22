package univ.airconnect.verification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.domain.VerificationPurpose;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;
import univ.airconnect.verification.repository.VerifiedSchoolEmailRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationReplayProtectionTest {
    @Mock private MailService mail;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private UserRepository users;
    @Mock private UserMilestoneRepository milestones;
    @Mock private VerifiedSchoolEmailRepository emails;
    @Mock private AttemptThrottleService throttle;
    @Mock private TicketLedgerRepository ledger;
    private VerificationService service;
    private static final String EMAIL = "fixture@office.hanseo.ac.kr";

    @BeforeEach
    void setUp() {
        service = new VerificationService(mail, redis, users, milestones, emails,
                new MilestoneRewardProperties(), throttle, ledger);
    }

    @Test
    void changingIpDoesNotBypassTheEmailCodeAttemptBudget() {
        when(redis.execute(VerificationService.CONSUME_CODE,
                List.of("email_verification:" + EMAIL, "email_verification_attempts:" + EMAIL),
                "111111", "5", "300")).thenReturn(-2L);

        for (String ip : List.of("203.0.113.1", "203.0.113.2")) {
            assertThatThrownBy(() -> service.verifyCode(null, EMAIL, "111111", VerificationPurpose.SIGN_UP, ip))
                    .isInstanceOf(VerificationException.class)
                    .extracting("errorCode").isEqualTo(VerificationErrorCode.TOO_MANY_ATTEMPTS);
        }
        verifyNoInteractions(users, emails, ledger);
    }

    @Test
    void consumedCodeCannotIssueASecondVerificationToken() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.execute(VerificationService.CONSUME_CODE,
                List.of("email_verification:" + EMAIL, "email_verification_attempts:" + EMAIL),
                "123456", "5", "300")).thenReturn(1L, 0L);

        assertThat(service.verifyCode(null, EMAIL, "123456", VerificationPurpose.LOGIN).verificationToken()).isNotBlank();
        assertThatThrownBy(() -> service.verifyCode(null, EMAIL, "123456", VerificationPurpose.LOGIN))
                .isInstanceOf(VerificationException.class)
                .extracting("errorCode").isEqualTo(VerificationErrorCode.CODE_EXPIRED);
        verify(emails, times(1)).save(any());
    }

    @Test
    void verifiedTokenIsConsumedWithAtomicReadAndDelete() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("email_verified_token:fixture-token")).thenReturn(EMAIL, null);

        assertThat(service.consumeVerifiedEmail("fixture-token")).isEqualTo(EMAIL);
        assertThatThrownBy(() -> service.consumeVerifiedEmail("fixture-token"))
                .isInstanceOf(VerificationException.class)
                .extracting("errorCode").isEqualTo(VerificationErrorCode.VERIFIED_EMAIL_TOKEN_EXPIRED);
        verify(values, never()).get("email_verified_token:fixture-token");
    }

    @Test
    void concurrentResendCannotOverwriteAnActiveCode() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("email_verification_cooldown:" + EMAIL, "1", 60L, TimeUnit.SECONDS))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendCode(EMAIL, VerificationPurpose.LOGIN))
                .isInstanceOf(VerificationException.class)
                .extracting("errorCode").isEqualTo(VerificationErrorCode.TOO_MANY_REQUESTS);
        verifyNoInteractions(mail);
        verify(redis, never()).delete(any(String.class));
    }
}
