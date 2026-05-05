package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.security.jwt.JwtAuthenticationFilter;

import java.io.IOException;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        AuthException tokenException =
                (AuthException) request.getAttribute(JwtAuthenticationFilter.AUTH_EXCEPTION_ATTRIBUTE);

        if (tokenException != null && tokenException.getErrorCode() != null) {
            writeResponse(response, traceId, tokenException.getErrorCode(), tokenException.getMessage());
            return;
        }

        writeResponse(response, traceId, ErrorCode.UNAUTHORIZED);
    }

    private void writeResponse(
            HttpServletResponse response,
            String traceId,
            AuthErrorCode errorCode,
            String message
    ) throws IOException {
        ErrorBody body = new ErrorBody(
                errorCode.getCode(),
                message,
                errorCode.getHttpStatus().value(),
                traceId,
                null
        );

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader(TRACE_ID_HEADER, traceId);
        }
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(body, traceId));
    }

    private void writeResponse(
            HttpServletResponse response,
            String traceId,
            ErrorCode errorCode
    ) throws IOException {
        ErrorBody body = new ErrorBody(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getHttpStatus().value(),
                traceId,
                null
        );

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader(TRACE_ID_HEADER, traceId);
        }
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(body, traceId));
    }
}
