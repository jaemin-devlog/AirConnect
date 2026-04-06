package univ.airconnect.global.security.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserIdArgumentResolverTest {

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveArgument_throwsWhenUserDeleted() {
        Long userId = 101L;
        CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver(userRepository);

        User deletedUser = User.create(SocialProvider.KAKAO, "social-101", "u101@test.dev");
        ReflectionTestUtil.setId(deletedUser, userId);
        deletedUser.markDeleted();

        when(userRepository.findById(userId)).thenReturn(Optional.of(deletedUser));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserPrincipal(userId),
                        null
                )
        );

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_DELETED);
    }

    @Test
    void resolveArgument_returnsUserIdForActiveUser() throws Exception {
        Long userId = 202L;
        CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver(userRepository);

        User user = User.create(SocialProvider.KAKAO, "social-202", "u202@test.dev");
        ReflectionTestUtil.setId(user, userId);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserPrincipal(userId),
                        null
                )
        );

        Object resolved = resolver.resolveArgument(null, null, null, null);
        assertThat(resolved).isEqualTo(userId);
    }

    private static final class ReflectionTestUtil {
        private static void setId(User user, Long id) {
            org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        }
    }
}
