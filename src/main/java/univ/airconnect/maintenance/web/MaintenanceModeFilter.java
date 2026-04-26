package univ.airconnect.maintenance.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final MaintenanceService maintenanceService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri.startsWith("/api/v1/admin")
                || uri.startsWith("/api/v1/auth")
                || uri.startsWith("/api/v1/verification")
                || uri.startsWith("/api/v1/maintenance")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator/health")
                || uri.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!maintenanceService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = resolveTraceId(request);
        MaintenanceStatusResponse status = maintenanceService.getStatus();
        ErrorCode errorCode = ErrorCode.MAINTENANCE_MODE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", status.enabled());
        details.put("title", status.title());
        details.put("message", status.message());
        details.put("startedAt", status.startedAt());
        details.put("updatedAt", status.updatedAt());

        ErrorBody errorBody = new ErrorBody(
                errorCode.getCode(),
                status.message(),
                errorCode.getHttpStatus().value(),
                traceId,
                details
        );

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(TRACE_ID_HEADER, traceId);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorBody, traceId));
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (attribute != null && !String.valueOf(attribute).isBlank()) {
            return String.valueOf(attribute);
        }

        String header = request.getHeader(TRACE_ID_HEADER);
        if (header != null && !header.isBlank()) {
            request.setAttribute(TRACE_ID_ATTRIBUTE, header);
            return header;
        }

        String generated = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, generated);
        return generated;
    }
}
