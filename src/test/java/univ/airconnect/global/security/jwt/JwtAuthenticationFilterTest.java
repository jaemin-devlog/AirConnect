package univ.airconnect.global.security.jwt;

import jakarta.servlet.ServletException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter jwtAuthenticationFilter =
            new JwtAuthenticationFilter(jwtProvider, userRepository, new ObjectMapper());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAdminAuthorityWhenAdminUserAuthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtProvider.getUserId("access-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserRole.ADMIN, UserStatus.ACTIVE)));

        jwtAuthenticationFilter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void clearsAuthenticationWhenUserIsSuspended() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtProvider.getUserId("access-token")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, UserRole.ADMIN, UserStatus.SUSPENDED)));

        jwtAuthenticationFilter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void storesAuthExceptionWhenAccessTokenIsExpired() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthException authException = new AuthException(AuthErrorCode.TOKEN_EXPIRED);

        doThrow(authException).when(jwtProvider).validateAccessToken("expired-token");

        jwtAuthenticationFilter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_EXCEPTION_ATTRIBUTE)).isSameAs(authException);
    }

    @Test
    void redisFailureReturnsSanitized503AndStopsFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer secret-access-token");
        request.setAttribute(univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE, "safe-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);
        doThrow(new RedisConnectionFailureException("secret-access-token internal.redis:6379"))
                .when(jwtProvider).validateAccessToken("secret-access-token");

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("httpStatus").asInt()).isEqualTo(503);
        assertThat(body.path("traceId").asText()).isEqualTo("safe-trace");
        assertThat(response.getContentAsString()).doesNotContain("secret-access-token", "internal.redis", "Exception");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain, userRepository);
    }

    @Test
    void unrelatedProgrammingFailureIsNotHiddenAsDependencyOutage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        doThrow(new IllegalStateException("programming failure")).when(jwtProvider).validateAccessToken("token");
        assertThatThrownBy(() -> jwtAuthenticationFilter.doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain()))
                .isInstanceOf(IllegalStateException.class);
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("social-" + id)
                .email("user" + id + "@airconnect.test")
                .status(status)
                .role(role)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(10)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
