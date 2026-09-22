package univ.airconnect.global.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MalformedJsonSecurityTest {
    @Test
    void accountStatusErrorOnPurchaseLookupDoesNotLogToken(CapturedOutput output) {
        String sentinel = "dummy-sensitive-purchase-token";
        var request = new MockHttpServletRequest("GET", "/api/v1/iap/android/purchases/" + sentinel);
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/iap/android/purchases/{purchaseToken}");
        var result = new GlobalExceptionHandler().handleUser(new univ.airconnect.user.exception.UserException(
                univ.airconnect.user.exception.UserErrorCode.USER_DELETED), request);
        assertThat(result.getStatusCode().value()).isEqualTo(403);
        assertThat(result.getBody().getError().code()).isEqualTo("USER_DELETED");
        assertThat(output.getAll()).contains("{purchaseToken}").doesNotContain(sentinel);
    }

    @Test
    void unexpectedExceptionAndUnsupportedPurchasePathDoNotLogSecrets(CapturedOutput output) {
        String sentinel = "dummy-sensitive-purchase-token";
        var request = new MockHttpServletRequest("POST", "/api/v1/iap/android/purchases/" + sentinel);
        var handler = new GlobalExceptionHandler();
        handler.handleUnknown(new IllegalStateException("database key=" + sentinel), request);
        handler.handleMethodNotSupported(
                new org.springframework.web.HttpRequestMethodNotSupportedException("POST"), request);
        assertThat(output.getAll()).doesNotContain(sentinel, "database key=");
        assertThat(output.getAll()).contains("IllegalStateException");
    }

    @Test
    void invalidJsonReturns400WithoutLoggingInputOrParserMessage(CapturedOutput output) {
        String sentinel = "dummy-sensitive-purchase-token";
        var exception = new HttpMessageNotReadableException("Cannot deserialize value: " + sentinel,
                new IllegalArgumentException(sentinel), new MockHttpInputMessage(new byte[0]));
        var result = new GlobalExceptionHandler().handleUnreadableBody(exception, new MockHttpServletRequest());
        assertThat(result.getStatusCode().value()).isEqualTo(400);
        assertThat(result.getBody().getError().code()).isEqualTo("COMMON-001");
        assertThat(result.getBody().getError().details()).isNull();
        assertThat(output.getAll()).doesNotContain(sentinel, "Cannot deserialize");
    }
}
