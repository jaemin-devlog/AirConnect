package univ.airconnect.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import univ.airconnect.global.response.ApiResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

class GlobalExceptionHandlerTest {

    @Test
    void handleNoResourceFound_returnsNotFoundApiResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String traceId = "trace-123";
        String path = "/api/v1/matching/team-rooms/9/chat/messages";

        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), path);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);

        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, path);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFound(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
        assertThat(response.getBody().getError()).isNotNull();
        assertThat(response.getBody().getError().code()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(response.getBody().getError().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getBody().getError().traceId()).isEqualTo(traceId);
        assertThat(response.getBody().getError().details())
                .isInstanceOf(Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("method", HttpMethod.GET.name())
                .containsEntry("path", path);
    }
}
