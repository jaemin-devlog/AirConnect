package univ.airconnect.verification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.global.security.AttemptThrottleService;
import univ.airconnect.verification.domain.VerificationNextAction;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.domain.VerificationPurpose;
import univ.airconnect.verification.domain.entity.VerifiedSchoolEmail;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;
import univ.airconnect.verification.repository.VerifiedSchoolEmailRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private MailService mailService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private VerifiedSchoolEmailRepository verifiedSchoolEmailRepository;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AttemptThrottleService attemptThrottleService;

    @Test
    void verifyCode_grantsOneTicket_whenEmailRewardUsesDefaultValue() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();

        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        Long userId = 1L;
        String email = "student@office.hanseo.ac.kr";
        String code = "123456";

        User user = User.create(SocialProvider.KAKAO, "social-1", email);
        ReflectionTestUtils.setField(user, "id", userId);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, MilestoneType.EMAIL_VERIFIED))
                .thenReturn(false);
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        int before = user.getTickets();

        VerifiedEmailSession session = service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP);

        assertThat(session.verificationToken()).isNotBlank();
        assertThat(user.getTickets()).isEqualTo(before + 1);
        verify(userMilestoneRepository).save(any());
    }

    @Test
    void verifyCode_doesNotGrantTicket_whenEmailRewardIsZero() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        rewardProperties.setEmailVerifiedTickets(0);
        rewardProperties.setProfileImageUploadedTickets(2);

        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        Long userId = 1L;
        String email = "student@office.hanseo.ac.kr";
        String code = "123456";

        User user = User.create(SocialProvider.KAKAO, "social-1", email);
        ReflectionTestUtils.setField(user, "id", userId);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, MilestoneType.EMAIL_VERIFIED))
                .thenReturn(false);
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        int before = user.getTickets();

        VerifiedEmailSession session = service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP);

        assertThat(session.verificationToken()).isNotBlank();
        assertThat(session.email()).isEqualTo(email);
        assertThat(session.nextAction()).isEqualTo(VerificationNextAction.SIGN_UP);
        assertThat(user.getTickets()).isEqualTo(before);
        verify(userMilestoneRepository).save(any());
        verify(redisTemplate).delete(eq("email_verification:" + email));
        verify(redisTemplate).delete(eq("email_verification_cooldown:" + email));
    }

    @Test
    void sendCode_reissues_whenVerifiedTokenAlreadyIssuedForEmail() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email_verification_cooldown:" + email)).thenReturn(false);
        when(valueOperations.get("email_verified_active:" + email)).thenReturn("active-token");

        service.sendCode(email, VerificationPurpose.SIGN_UP);

        verify(redisTemplate).delete("email_verified_active:" + email);
        verify(redisTemplate).delete("email_verified_token:active-token");
        verify(mailService).sendVerificationCode(eq(email), anyString());
    }

    @Test
    void sendCode_signupPurpose_sendsMailEvenWhenEmailAlreadyRegistered() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";
        User existing = User.createEmailUser(email, "encoded-password");
        ReflectionTestUtils.setField(existing, "id", 101L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email_verification_cooldown:" + email)).thenReturn(false);

        service.sendCode(email, VerificationPurpose.SIGN_UP);

        verify(mailService).sendVerificationCode(eq(email), anyString());
    }

    @Test
    void sendCode_signupPurpose_sendsMailEvenWhenEmailAlreadyLinkedToSocialAccount() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email_verification_cooldown:" + email)).thenReturn(false);

        service.sendCode(email, VerificationPurpose.SIGN_UP);

        verify(mailService).sendVerificationCode(eq(email), anyString());
    }

    @Test
    void sendCode_signupPurpose_sendsMailEvenWhenVerifiedSchoolEmailAlreadyReserved() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email_verification_cooldown:" + email)).thenReturn(false);

        service.sendCode(email, VerificationPurpose.SIGN_UP);

        verify(mailService).sendVerificationCode(eq(email), anyString());
    }

    @Test
    void verifyCode_linksVerifiedSchoolEmailToAuthenticatedUser() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        rewardProperties.setEmailVerifiedTickets(0);

        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        Long userId = 10L;
        String email = "student@office.hanseo.ac.kr";
        String code = "123456";
        User user = User.create(SocialProvider.APPLE, "apple-social-10", "relay@privaterelay.appleid.com");
        ReflectionTestUtils.setField(user, "id", userId);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, MilestoneType.EMAIL_VERIFIED))
                .thenReturn(false);
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP);

        assertThat(user.getEmail()).isEqualTo("relay@privaterelay.appleid.com");
        assertThat(user.getPrimaryEmail()).isEqualTo(email);
    }

    @Test
    void verifyCode_withoutAuthenticatedUser_persistsVerifiedSchoolEmailReservation() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        rewardProperties.setEmailVerifiedTickets(0);

        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";
        String code = "123456";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        service.verifyCode(null, email, code, VerificationPurpose.SIGN_UP);

        verify(verifiedSchoolEmailRepository).save(any(VerifiedSchoolEmail.class));
    }

    @Test
    void verifyCode_failsWhenEmailAlreadyLinkedToAnotherAccountForAuthenticatedUser() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";
        String code = "123456";
        Long userId = 202L;
        User user = User.create(SocialProvider.KAKAO, "kakao-social-202", null);
        ReflectionTestUtils.setField(user, "id", userId);
        VerifiedSchoolEmail reservation = VerifiedSchoolEmail.reserve(email, 101L);
        ReflectionTestUtils.setField(reservation, "id", 77L);

        when(attemptThrottleService.isLocked("school_email_verify", email, "203.0.113.9")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(verifiedSchoolEmailRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP, "203.0.113.9"))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.ALREADY_REGISTERED_EMAIL);
    }

    @Test
    void verifyCode_failsWhenAttemptLockIsActive() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(attemptThrottleService.isLocked("school_email_verify", email, "203.0.113.9")).thenReturn(true);

        assertThatThrownBy(() -> service.verifyCode(null, email, "123456", VerificationPurpose.SIGN_UP, "203.0.113.9"))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.TOO_MANY_ATTEMPTS);
    }

    @Test
    void sendCode_failsWhenEmailAndIpSendLimitIsActive() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(attemptThrottleService.isLocked("school_email_send", email, "203.0.113.9")).thenReturn(true);

        assertThatThrownBy(() -> service.sendCode(email, VerificationPurpose.SIGN_UP, "203.0.113.9"))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.TOO_MANY_REQUESTS);

        verify(mailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void sendCode_failsWhenIpWideSendLimitIsActive() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                verifiedSchoolEmailRepository,
                rewardProperties,
                attemptThrottleService
        );

        String email = "student@office.hanseo.ac.kr";

        when(attemptThrottleService.isLocked("school_email_send", email, "203.0.113.9")).thenReturn(false);
        when(attemptThrottleService.isLocked("school_email_send_ip", "203.0.113.9", "global")).thenReturn(true);

        assertThatThrownBy(() -> service.sendCode(email, VerificationPurpose.SIGN_UP, "203.0.113.9"))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.TOO_MANY_REQUESTS);

        verify(mailService, never()).sendVerificationCode(any(), any());
    }
}
