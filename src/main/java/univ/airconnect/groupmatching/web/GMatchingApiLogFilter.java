package univ.airconnect.groupmatching.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.reflect.Method;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@Component
public class GMatchingApiLogFilter extends OncePerRequestFilter {

    private static final String MATCHING_PREFIX = "/api/v1/matching/team-rooms";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(MATCHING_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                return;
            }

            int status = response.getStatus();
            if (status >= 400) {
                return;
            }

            String traceId = traceIdOrDash(request);
            String userId = currentUserIdOrDash();
            String method = request.getMethod();
            String uriWithQuery = request.getRequestURI();
            String userAgent = headerOrDash(request, "User-Agent");
            String origin = headerOrDash(request, "Origin");
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isBlank()) {
                uriWithQuery += "?" + queryString;
            }

            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info(
                    "[GMATCH][API-SUCCESS] [{}] {} {} status={} userId={} ua='{}' origin='{}' elapsedMs={}",
                    traceId,
                    method,
                    uriWithQuery,
                    status,
                    userId,
                    userAgent,
                    origin,
                    elapsedMs
            );
        }
    }

    private String traceIdOrDash(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (traceId == null) {
            return "-";
        }
        String value = String.valueOf(traceId);
        return value.isBlank() ? "-" : value;
    }

    private String currentUserIdOrDash() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "-";
        }

        Object principal = authentication.getPrincipal();
        if (principal != null) {
            try {
                Method method = principal.getClass().getMethod("getId");
                Object value = method.invoke(principal);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception ignored) {
            }
        }

        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return "-";
        }
        return name;
    }

    private String headerOrDash(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
