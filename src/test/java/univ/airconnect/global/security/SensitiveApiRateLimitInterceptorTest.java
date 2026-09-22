package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.UserRole;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SensitiveApiRateLimitInterceptorTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SensitiveApiRateLimitInterceptor limiter =
            new SensitiveApiRateLimitInterceptor(redis, new ObjectMapper());
    private final HandlerMethod handler = mock(HandlerMethod.class);

    @BeforeEach
    void authenticate() {
        var principal = new CustomUserPrincipal(7L, UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest coupon() {
        var request = new MockHttpServletRequest("POST", "/api/v1/tickets/coupons/redeem");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/tickets/coupons/redeem");
        return request;
    }

    @Test
    void validAttemptsContinueAndSpoofedIpDoesNotChangeBucket() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        var request = coupon();
        request.addHeader("X-Forwarded-For", "spoofed-ip");
        assertThat(limiter.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        verify(redis).execute(any(RedisScript.class), eq(List.of(
                "security_api_limit:{7}:coupon:minute", "security_api_limit:{7}:coupon:hour")),
                eq("5"), eq("60"), eq("30"), eq("3600"));
    }

    @Test
    void quotaFailureUsesExistingJsonEnvelopeAndRetryAfter() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(48L);
        var response = new MockHttpServletResponse();
        assertThat(limiter.preHandle(coupon(), response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("48");
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("COMMON-429");
    }

    @Test
    void storeFailureFailsClosedWithoutLeakingException() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("private backend address"));
        var response = new MockHttpServletResponse();
        assertThat(limiter.preHandle(coupon(), response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("private backend address");
    }

    @Test
    void unrelatedEndpointsDoNotDependOnLimiter() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/users/me");
        assertThat(limiter.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/matching/team-rooms/join-by-invite",
            "POST,/api/v1/users/profile-image",
            "POST,/api/v1/ads/rewards/session",
            "POST,/api/v1/iap/ios/transactions/verify",
            "POST,/api/v1/iap/android/purchases/verify",
            "POST,/api/v1/iap/ios/transactions/sync",
            "POST,/api/v1/iap/android/purchases/sync",
            "GET,/api/v1/compatibility/{targetUserId}"
    })
    void sensitiveRoutesEnforceQuota(String method, String route) throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(10L);
        var request = new MockHttpServletRequest(method, route);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
        var response = new MockHttpServletResponse();
        assertThat(limiter.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }
}
