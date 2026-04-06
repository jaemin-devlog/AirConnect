package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleSignedTransactionVerifierTest {

    private final AppleSignedTransactionVerifier verifier = new AppleSignedTransactionVerifier(new ObjectMapper());

    @Test
    void verifyAndExtractPayload_throwsInvalidTransaction_whenJwsFormatIsInvalid() {
        assertThatThrownBy(() -> verifier.verifyAndExtractPayload("invalid-jws"))
                .isInstanceOf(IapException.class)
                .extracting("errorCode")
                .isEqualTo(IapErrorCode.IAP_INVALID_TRANSACTION);
    }

    @Test
    void verifyAndExtractPayload_throwsInvalidTransaction_whenAlgorithmIsNotEs256() {
        String header = base64Url("{\"alg\":\"none\",\"x5c\":[\"dummy-cert\"]}");
        String payload = base64Url("{\"bundleId\":\"com.airconnect.app\"}");
        String jws = header + "." + payload + ".signature";

        assertThatThrownBy(() -> verifier.verifyAndExtractPayload(jws))
                .isInstanceOf(IapException.class)
                .extracting("errorCode")
                .isEqualTo(IapErrorCode.IAP_INVALID_TRANSACTION);
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
