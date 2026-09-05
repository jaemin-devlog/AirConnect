package univ.airconnect.user.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDeletedAccountRestoreTest {

    @Test
    void socialAccountRestorationKeepsAnonymizedFieldsEmptyAndRequiresOnboarding() {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("retained-social-id")
                .email("before-delete@example.test")
                .name("삭제 전 이름")
                .nickname("삭제 전 별명")
                .tickets(7)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .build();

        user.anonymizeForDeletion();
        user.markDeleted();
        user.restoreDeletedSocialAccount();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getSocialId()).isEqualTo("retained-social-id");
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.BASIC);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getTickets()).isEqualTo(7);
    }

    @Test
    void emailAccountRestorationIsRejectedAfterPasswordDeletion() {
        User user = User.createEmailUser("withdrawn@example.test", "password-hash");
        ReflectionTestUtils.setField(user, "id", 10L);
        user.anonymizeForDeletion();
        user.markDeleted();

        assertThatThrownBy(user::restoreDeletedSocialAccount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password deletion");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
    }
}
