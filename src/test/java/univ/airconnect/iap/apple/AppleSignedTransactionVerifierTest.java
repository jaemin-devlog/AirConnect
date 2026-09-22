package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
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
    void malformedSignedPayloadIsNotEchoedIntoProviderFailureLogs() {
        String privateFixture = "synthetic-private-purchase-token-do-not-log";
        Logger logger = (Logger) LoggerFactory.getLogger(AppleSignedTransactionVerifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String malformed = base64Url("{\"alg\":\"ES256\",\"private\": " + privateFixture) + ".e30.signature";
            assertThatThrownBy(() -> verifier.verifyAndExtractPayload(malformed)).isInstanceOf(IapException.class);
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(privateFixture);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
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

        assertThat(trustAnchors.stream().map(TrustAnchor::getTrustedCert).toList())
                .containsExactlyInAnyOrderElementsOf(bundledCertificates.stream()
                        .filter(cert -> cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal()))
                        .toList());

        // Validate the real bundled Apple WWDR G6 -> Apple Root CA G3 chain, without production tokens.
        X509Certificate intermediate = bundledCertificates.stream()
                .filter(cert -> cert.getSubjectX500Principal().getName().contains("OU=G6"))
                .findFirst().orElseThrow();
        PKIXParameters parameters = new PKIXParameters(trustAnchors);
        parameters.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(
                CertificateFactory.getInstance("X.509").generateCertPath(List.of(intermediate)), parameters);
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
