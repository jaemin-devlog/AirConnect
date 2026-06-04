package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import univ.airconnect.global.security.principal.CustomUserPrincipal;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminApiAuditInterceptor implements HandlerInterceptor {

    private static final String START_NANOS_ATTRIBUTE = "adminApiAuditStartNanos";
    private static final String ADMIN_API_PREFIX = "/api/v1/admin/";

    private final AdminAuditLogService adminAuditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldAudit(request)) {
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
        if (!shouldAudit(request)) {
            return;
        }

        try {
            adminAuditLogService.recordApiCall(
                    currentUserId(),
                    request.getMethod(),
                    apiPath(request),
                    response.getStatus(),
                    durationMs(request),
                    (String) request.getAttribute(TRACE_ID_ATTRIBUTE)
            );
        } catch (Exception auditException) {
            log.warn("Admin API audit logging failed. method={}, uri={}, reason={}",
                    request.getMethod(), request.getRequestURI(), auditException.getMessage());
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_API_PREFIX)
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
