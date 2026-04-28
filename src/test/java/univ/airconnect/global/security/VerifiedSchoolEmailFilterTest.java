package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VerifiedSchoolEmailFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final VerifiedSchoolEmailFilter filter =
            new VerifiedSchoolEmailFilter(userRepository, new ObjectMapper());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksProtectedEndpointForUnverifiedUser() throws ServletException, IOException {
        Long userId = 10L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/matching/recommendations");
        request.setAttribute("traceId", "trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(userId, UserRole.USER), null)
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, false, UserRole.USER)));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("SCHOOL_EMAIL_VERIFICATION_REQUIRED");
    }

    @Test
    void allowsOnboardingEndpointForUnverifiedUser() throws ServletException, IOException {
        Long userId = 11L;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/sign-up");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(userId, UserRole.USER), null)
        );

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(userRepository);
    }

    @Test
    void allowsProtectedEndpointForVerifiedUser() throws ServletException, IOException {
        Long userId = 12L;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/matching/recommendations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserPrincipal(userId, UserRole.USER), null)
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, true, UserRole.USER)));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private User user(Long id, boolean verified, UserRole role) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("social-" + id)
                .email("user" + id + "@airconnect.test")
                .verifiedSchoolEmail(verified ? "student" + id + "@office.hanseo.ac.kr" : null)
                .status(UserStatus.ACTIVE)
                .role(role)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(10)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
