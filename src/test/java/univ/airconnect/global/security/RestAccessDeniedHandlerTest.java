package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

class RestAccessDeniedHandlerTest {

    private final RestAccessDeniedHandler accessDeniedHandler =
            new RestAccessDeniedHandler(new ObjectMapper());

    @Test
    void writesJsonForbiddenResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(TRACE_ID_ATTRIBUTE, "trace-789");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo("trace-789");
        assertThat(response.getContentAsString()).contains("\"code\":\"AUTH-002\"");
        assertThat(response.getContentAsString()).contains("\"message\":\"권한이 없습니다.\"");
    }
}
