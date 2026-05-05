package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.global.security.jwt.JwtAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint =
            new RestAuthenticationEntryPoint(new ObjectMapper());

    @Test
    void writesExpiredTokenResponseWhenJwtFilterStoredAuthException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TRACE_ID_ATTRIBUTE, "trace-123");
        request.setAttribute(
                JwtAuthenticationFilter.AUTH_EXCEPTION_ATTRIBUTE,
                new AuthException(AuthErrorCode.TOKEN_EXPIRED)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Full authentication is required")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH_EXPIRED_TOKEN\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"토큰이 만료되었습니다.\"");
    }

    @Test
    void writesGenericUnauthorizedResponseWhenNoJwtExceptionExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TRACE_ID_ATTRIBUTE, "trace-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Full authentication is required")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH-001\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"인증이 필요합니다.\"");
    }
}
