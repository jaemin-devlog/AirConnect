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
    void socialAccountRestorationKeepsRetainedAccountFieldsAndHistoryMarkers() {
        LocalDateTime lastActiveAt = LocalDateTime.now().minusDays(2);
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("retained-social-id")
                .email("before-delete@example.test")
                .name("삭제 전 이름")
                .nickname("삭제 전 별명")
                .tickets(7)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now().minusMonths(3))
                .lastActiveAt(lastActiveAt)
                .build();

        user.markDeleted();
        user.restoreDeletedAccount();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getSocialId()).isEqualTo("retained-social-id");
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.FULL);
        assertThat(user.getEmail()).isEqualTo("before-delete@example.test");
        assertThat(user.getName()).isEqualTo("삭제 전 이름");
        assertThat(user.getNickname()).isEqualTo("삭제 전 별명");
        assertThat(user.getTickets()).isEqualTo(7);
        assertThat(user.getLastActiveAt()).isEqualTo(lastActiveAt);
    }

    @Test
    void emailAccountRestorationKeepsPasswordAndReturnsAccountToActive() {
        User user = User.createEmailUser("withdrawn@example.test", "password-hash");
        ReflectionTestUtils.setField(user, "id", 10L);
        user.completeSignUp("이름", "별명", 20260001, "학과");
        user.markDeleted();

        user.restoreDeletedAccount();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("password-hash");
        assertThat(user.getName()).isEqualTo("이름");
        assertThat(user.getNickname()).isEqualTo("별명");
        assertThat(user.getOnboardingStatus()).isEqualTo(OnboardingStatus.FULL);
    }

    @Test
    void legacyEmailAccountWithoutPasswordCannotBeRestored() {
        User user = User.createEmailUser("legacy@example.test", "password-hash");
        user.anonymizeForDeletion();
        user.markDeleted();

        assertThat(user.canRestoreDeletedAccount()).isFalse();
        assertThatThrownBy(user::restoreDeletedAccount)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }
}
