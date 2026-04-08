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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(valueOperations.get("email_verification:" + email)).thenReturn(code);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, MilestoneType.EMAIL_VERIFIED))
                .thenReturn(false);

        int before = user.getTickets();

        VerifiedEmailSession session = service.verifyCode(userId, email, code);

        assertThat(session.verificationToken()).isNotBlank();
        assertThat(session.email()).isEqualTo(email);
        assertThat(user.getTickets()).isEqualTo(before);
        verify(userMilestoneRepository).save(any());
        verify(redisTemplate).delete(eq("email_verification:" + email));
        verify(redisTemplate).delete(eq("email_verification_cooldown:" + email));
    }
}
