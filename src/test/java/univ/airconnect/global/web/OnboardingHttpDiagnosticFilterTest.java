package univ.airconnect.global.web;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

class OnboardingHttpDiagnosticFilterTest {

    private final OnboardingHttpDiagnosticFilter filter = new OnboardingHttpDiagnosticFilter();

    @Test
    void signUpPassesThroughWithoutChangingResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/sign-up");
        request.setAttribute(TRACE_ID_ATTRIBUTE, "trace-sign-up");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) res;
            httpResponse.setStatus(200);
            httpResponse.setHeader(TRACE_ID_HEADER, "trace-sign-up");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(TRACE_ID_HEADER)).isEqualTo("trace-sign-up");
    }

    @Test
    void userMePassesThroughErrorStatusWithoutChangingResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setAttribute(TRACE_ID_ATTRIBUTE, "trace-me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                ((HttpServletResponse) res).setStatus(401));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void unrelatedEndpointIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/chat/rooms");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
