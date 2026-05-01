package univ.airconnect.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.infrastructure.AdminAccountProperties;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountBootstrapServiceTest {

    @Mock
    private AdminAccountService adminAccountService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminAccountBootstrapService adminAccountBootstrapService;

    @Test
    void ensureAdminAccount_promotesAndSyncsConfiguredAccount() {
        AdminAccountProperties properties = new AdminAccountProperties(
                true,
                "admin@airconnect.test",
                "super-secret",
                "Ops Admin",
                "opsadmin",
                "운영팀",
                99999999
        );
        User user = User.createEmailUser("admin@airconnect.test", "old-hash");
        ReflectionTestUtils.setField(user, "id", 501L);

        when(adminAccountService.isEnabledAndConfigured()).thenReturn(true);
        when(adminAccountService.properties()).thenReturn(properties);
        when(adminAccountService.normalizedEmail()).thenReturn("admin@airconnect.test");
        when(adminAccountService.rawPassword()).thenReturn("super-secret");
        when(userRepository.findByProviderAndSocialId(SocialProvider.EMAIL, "admin@airconnect.test"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("super-secret", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("super-secret")).thenReturn("new-hash");

        adminAccountBootstrapService.ensureAdminAccount();

        assertThat(user.isAdmin()).isTrue();
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getName()).isEqualTo("Ops Admin");
        assertThat(user.getNickname()).isEqualTo("opsadmin");
        assertThat(user.getDeptName()).isEqualTo("운영팀");
        assertThat(user.getStudentNum()).isEqualTo(99999999);
    }
}
