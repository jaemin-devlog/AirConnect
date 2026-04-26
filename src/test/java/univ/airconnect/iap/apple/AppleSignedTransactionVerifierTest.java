package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @SuppressWarnings("unchecked")
    void verifier_loadsBundledAppleCertificatesAndTrustAnchors() throws Exception {
        Method loadBundledCertificates = AppleSignedTransactionVerifier.class.getDeclaredMethod("loadBundledCertificates");
        loadBundledCertificates.setAccessible(true);
        List<X509Certificate> bundledCertificates = (List<X509Certificate>) loadBundledCertificates.invoke(verifier);

        assertThat(bundledCertificates)
                .extracting(cert -> cert.getSubjectX500Principal().getName())
                .anyMatch(subject -> subject.contains("Apple Root CA - G3"))
                .anyMatch(subject -> subject.contains("Apple Worldwide Developer Relations"));

        Method loadTrustAnchors = AppleSignedTransactionVerifier.class.getDeclaredMethod("loadTrustAnchors");
        loadTrustAnchors.setAccessible(true);
        Set<TrustAnchor> trustAnchors = (Set<TrustAnchor>) loadTrustAnchors.invoke(verifier);

        assertThat(trustAnchors)
                .extracting(anchor -> anchor.getTrustedCert().getSubjectX500Principal().getName())
                .anyMatch(subject -> subject.contains("Apple Root CA - G3"));
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
