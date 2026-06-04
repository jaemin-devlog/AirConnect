package univ.airconnect.analytics.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import univ.airconnect.analytics.service.ApiRequestLogService;
import univ.airconnect.global.security.principal.CustomUserPrincipal;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiRequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_NANOS_ATTRIBUTE = "apiRequestLoggingStartNanos";

    private final ApiRequestLogService apiRequestLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldLog(request)) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        if (!shouldLog(request)) {
            return;
        }

        try {
            apiRequestLogService.record(
                    currentUserId(),
                    request.getMethod(),
                    apiPath(request),
                    response.getStatus(),
                    durationMs(request),
                    (String) request.getAttribute(TRACE_ID_ATTRIBUTE)
            );
        } catch (Exception loggingException) {
            log.warn("API request logging failed. method={}, uri={}, reason={}",
                    request.getMethod(), request.getRequestURI(), loggingException.getMessage());
        }
    }

    private boolean shouldLog(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null
                && requestUri.startsWith("/api/v1/")
                && !"OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private String apiPath(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String value && !value.isBlank()) {
            return value;
        }
        return request.getRequestURI();
    }

    private long durationMs(HttpServletRequest request) {
        Object startNanos = request.getAttribute(START_NANOS_ATTRIBUTE);
        if (!(startNanos instanceof Long startedAt)) {
            return 0L;
        }
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            return customUserPrincipal.getUserId();
        }
        return null;
    }
}
