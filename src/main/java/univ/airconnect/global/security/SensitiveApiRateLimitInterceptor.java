package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.security.principal.CustomUserPrincipal;

import java.io.IOException;
import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/** Shared Redis limits survive blue/green switches and cannot be reset by changing IP headers. */
@Component
@RequiredArgsConstructor
public class SensitiveApiRateLimitInterceptor implements HandlerInterceptor {

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            for i, key in ipairs(KEYS) do
                local count = tonumber(redis.call('GET', key) or '0')
                if count >= tonumber(ARGV[(i - 1) * 2 + 1]) then
                    return math.max(1, redis.call('TTL', key))
                end
            end
            for i, key in ipairs(KEYS) do
                local count = redis.call('INCR', key)
                if count == 1 then redis.call('EXPIRE', key, ARGV[(i - 1) * 2 + 2]) end
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod)) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) return true;
        // Use Spring's matched route, not a raw URI (context paths / encoding / aliases).
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Policy policy = policy(request.getMethod(), String.valueOf(route));
        if (policy == null) return true;
        String key = "security_api_limit:{" + principal.getUserId() + "}:" + policy.scope;
        Long retryAfter;
        try {
            retryAfter = redis.execute(CONSUME, List.of(key + ":minute", key + ":hour"),
                    Integer.toString(policy.perMinute), "60", Integer.toString(policy.perHour), "3600");
        } catch (RuntimeException unavailable) {
            // Do not allow unlimited guesses if the shared security store is unavailable.
            return reject(response, request, 503, "COMMON-503", "잠시 후 다시 시도해 주세요.", 5);
        }
        if (retryAfter == null) {
            return reject(response, request, 503, "COMMON-503", "잠시 후 다시 시도해 주세요.", 5);
        }
        return retryAfter <= 0 || reject(response, request, 429, "COMMON-429",
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
    }

    private Policy policy(String method, String route) {
        if ("POST".equals(method)) {
            return switch (route) {
                case "/api/v1/tickets/coupons/redeem" -> new Policy("coupon", 5, 30);
                case "/api/v1/matching/team-rooms/join-by-invite" -> new Policy("invite", 10, 60);
                case "/api/v1/users/profile-image" -> new Policy("profile_image", 10, 60);
                case "/api/v1/ads/rewards/session" -> new Policy("ad_session", 10, 60);
                case "/api/v1/iap/ios/transactions/verify", "/api/v1/iap/android/purchases/verify"
                        -> new Policy("iap_verify", 20, 120);
                case "/api/v1/iap/ios/transactions/sync", "/api/v1/iap/android/purchases/sync"
                        -> new Policy("iap_sync", 3, 20);
                default -> null;
            };
        }
        if ("GET".equals(method) && "/api/v1/compatibility/{targetUserId}".equals(route)) {
            return new Policy("compatibility", 30, 300);
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, HttpServletRequest request, int status,
                           String code, String message, long retryAfter) throws IOException {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfter));
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(
                new ErrorBody(code, message, status, traceId, null), traceId));
        return false;
    }

    private record Policy(String scope, int perMinute, int perHour) {}
}
