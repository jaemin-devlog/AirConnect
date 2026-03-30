package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.iap.application.StoreVerificationResult;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplePurchaseVerifierTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void verify_throwsMismatch_whenJwsTokenDiffersFromIssuedToken() {
        User user = User.create(SocialProvider.APPLE, "apple-social", "test@airconnect.com");
        Long userId = 1L;
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);

        IapProperties props = new IapProperties();
        props.getApple().setBundleId("com.airconnect.app");
        props.getApple().setEnvironment("SANDBOX");

        ApplePurchaseVerifier verifier = new ApplePurchaseVerifier(
                new ObjectMapper(),
                props,
                new PayloadSecurityUtil(),
                userRepository
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String jws = makeJws("com.airconnect.app", "com.airconnect.tickets.pack10", "tx-1", "different-token");
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest(jws, "tx-1", null);

        assertThatThrownBy(() -> verifier.verify(userId, request))
                .isInstanceOf(IapException.class)
                .extracting("errorCode")
                .isEqualTo(IapErrorCode.IAP_ACCOUNT_TOKEN_MISMATCH);
    }

    @Test
    void verify_success_whenJwsTokenMatchesIssuedToken() {
        User user = User.create(SocialProvider.APPLE, "apple-social", "test@airconnect.com");
        Long userId = 2L;
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);

        String issued = user.getIosAppAccountToken();

        IapProperties props = new IapProperties();
        props.getApple().setBundleId("com.airconnect.app");
        props.getApple().setEnvironment("SANDBOX");

        ApplePurchaseVerifier verifier = new ApplePurchaseVerifier(
                new ObjectMapper(),
                props,
                new PayloadSecurityUtil(),
                userRepository
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String jws = makeJws("com.airconnect.app", "com.airconnect.tickets.pack10", "tx-2", issued);
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest(jws, "tx-2", null);

        StoreVerificationResult result = verifier.verify(userId, request);

        assertThat(result.getAppAccountToken()).isEqualTo(issued);
        assertThat(result.getTransactionId()).isEqualTo("tx-2");
        assertThat(result.getProductId()).isEqualTo("com.airconnect.tickets.pack10");
    }

    private String makeJws(String bundleId, String productId, String txId, String appAccountToken) {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = "{" +
                "\"bundleId\":\"" + bundleId + "\"," +
                "\"productId\":\"" + productId + "\"," +
                "\"transactionId\":\"" + txId + "\"," +
                "\"appAccountToken\":\"" + appAccountToken + "\"," +
                "\"environment\":\"SANDBOX\"" +
                "}";
        String payloadEncoded = base64Url(payload);
        return header + "." + payloadEncoded + ".sig";
    }

    private String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}

