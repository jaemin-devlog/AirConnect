package univ.airconnect.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

/**
 * Records privacy-safe request completion facts for the two onboarding calls
 * needed to diagnose released-client failures. Request/response bodies,
 * credentials, headers, query parameters, and personal data are never logged.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OnboardingHttpDiagnosticFilter extends OncePerRequestFilter {

    private static final Set<String> DIAGNOSTIC_PATHS = Set.of(
            "/api/v1/users/sign-up",
            "/api/v1/users/me"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !DIAGNOSTIC_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAtNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
            String traceId = response.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                Object requestTraceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
                traceId = requestTraceId instanceof String value ? value : null;
            }

            log.info(
                    "Onboarding HTTP completed: method={}, path={}, status={}, durationMs={}, traceId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    traceId
            );
        }
    }
}
