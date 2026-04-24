package univ.airconnect.verification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.verification.domain.VerificationPurpose;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

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
    private ValueOperations<String, String> valueOperations;

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
                rewardProperties
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

        int before = user.getTickets();

        VerifiedEmailSession session = service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP);

        assertThat(session.verificationToken()).isNotBlank();
        assertThat(session.email()).isEqualTo(email);
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
                rewardProperties
        );

        String email = "student@office.hanseo.ac.kr";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email_verification_cooldown:" + email)).thenReturn(false);
        when(valueOperations.get("email_verified_active:" + email)).thenReturn("active-token");
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, email)).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase(email)).thenReturn(false);
        when(userRepository.existsByVerifiedSchoolEmailIgnoreCase(email)).thenReturn(false);

        service.sendCode(email, VerificationPurpose.SIGN_UP);

        verify(redisTemplate).delete("email_verified_active:" + email);
        verify(redisTemplate).delete("email_verified_token:active-token");
        verify(mailService).sendVerificationCode(eq(email), anyString());
    }

    @Test
    void sendCode_signupPurpose_failsWhenEmailAlreadyRegistered() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                rewardProperties
        );

        String email = "student@office.hanseo.ac.kr";
        User existing = User.createEmailUser(email, "encoded-password");
        ReflectionTestUtils.setField(existing, "id", 101L);

        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, email)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCase(email)).thenReturn(true);

        assertThatThrownBy(() -> service.sendCode(email, VerificationPurpose.SIGN_UP))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.ALREADY_REGISTERED_EMAIL);

        verify(mailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void sendCode_signupPurpose_failsWhenEmailAlreadyLinkedToSocialAccount() {
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        VerificationService service = new VerificationService(
                mailService,
                redisTemplate,
                userRepository,
                userMilestoneRepository,
                rewardProperties
        );

        String email = "student@office.hanseo.ac.kr";

        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, email)).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase(email)).thenReturn(false);
        when(userRepository.existsByVerifiedSchoolEmailIgnoreCase(email)).thenReturn(true);

        assertThatThrownBy(() -> service.sendCode(email, VerificationPurpose.SIGN_UP))
                .isInstanceOf(VerificationException.class)
                .extracting(ex -> ((VerificationException) ex).getErrorCode())
                .isEqualTo(VerificationErrorCode.ALREADY_REGISTERED_EMAIL);

        verify(mailService, never()).sendVerificationCode(any(), any());
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
                rewardProperties
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

        service.verifyCode(userId, email, code, VerificationPurpose.SIGN_UP);

        assertThat(user.getEmail()).isEqualTo("relay@privaterelay.appleid.com");
        assertThat(user.getPrimaryEmail()).isEqualTo(email);
    }
}
