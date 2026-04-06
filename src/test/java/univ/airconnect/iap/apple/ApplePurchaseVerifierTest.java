package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplePurchaseVerifierTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AppleSignedTransactionVerifier signedTransactionVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verify_throwsMismatch_whenJwsTokenDiffersFromIssuedToken() {
        User user = User.create(SocialProvider.APPLE, "apple-social", "test@airconnect.com");
        Long userId = 1L;
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);

        IapProperties props = new IapProperties();
        props.getApple().setBundleId("com.airconnect.app");
        props.getApple().setEnvironment("SANDBOX");

        ApplePurchaseVerifier verifier = new ApplePurchaseVerifier(
                props,
                new PayloadSecurityUtil(),
                userRepository,
                signedTransactionVerifier
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(signedTransactionVerifier.verifyAndExtractPayload(anyString()))
                .thenReturn(payloadNode("com.airconnect.app", "com.airconnect.tickets.pack10", "tx-1", "different-token"));

        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest("signed-jws", "tx-1", null);

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
                props,
                new PayloadSecurityUtil(),
                userRepository,
                signedTransactionVerifier
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(signedTransactionVerifier.verifyAndExtractPayload(anyString()))
                .thenReturn(payloadNode("com.airconnect.app", "com.airconnect.tickets.pack10", "tx-2", issued));

        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest("signed-jws", "tx-2", null);

        StoreVerificationResult result = verifier.verify(userId, request);

        assertThat(result.getAppAccountToken()).isEqualTo(issued);
        assertThat(result.getTransactionId()).isEqualTo("tx-2");
        assertThat(result.getProductId()).isEqualTo("com.airconnect.tickets.pack10");
    }

    @Test
    void verify_throwsInvalidProduct_whenProductNotAllowed() {
        User user = User.create(SocialProvider.APPLE, "apple-social", "test@airconnect.com");
        Long userId = 3L;
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);

        String issued = user.getIosAppAccountToken();

        IapProperties props = new IapProperties();
        props.getApple().setBundleId("com.airconnect.app");
        props.getApple().setEnvironment("SANDBOX");
        props.getApple().setAllowedProductIds(java.util.List.of("com.airconnect.tickets.pack10"));

        ApplePurchaseVerifier verifier = new ApplePurchaseVerifier(
                props,
                new PayloadSecurityUtil(),
                userRepository,
                signedTransactionVerifier
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(signedTransactionVerifier.verifyAndExtractPayload(anyString()))
                .thenReturn(payloadNode("com.airconnect.app", "com.airconnect.tickets.pack5", "tx-3", issued));

        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest("signed-jws", "tx-3", null);

        assertThatThrownBy(() -> verifier.verify(userId, request))
                .isInstanceOf(IapException.class)
                .extracting("errorCode")
                .isEqualTo(IapErrorCode.IAP_INVALID_PRODUCT);
    }

    @Test
    void verify_marksRevokedTransactionAsInvalid() {
        User user = User.create(SocialProvider.APPLE, "apple-social", "test@airconnect.com");
        Long userId = 4L;
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);

        String issued = user.getIosAppAccountToken();

        IapProperties props = new IapProperties();
        props.getApple().setBundleId("com.airconnect.app");
        props.getApple().setEnvironment("SANDBOX");

        ApplePurchaseVerifier verifier = new ApplePurchaseVerifier(
                props,
                new PayloadSecurityUtil(),
                userRepository,
                signedTransactionVerifier
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        JsonNode revokedPayload = objectMapper.createObjectNode()
                .put("bundleId", "com.airconnect.app")
                .put("productId", "com.airconnect.tickets.pack10")
                .put("transactionId", "tx-4")
                .put("appAccountToken", issued)
                .put("environment", "SANDBOX")
                .put("revocationDate", "1730000000000");
        when(signedTransactionVerifier.verifyAndExtractPayload(anyString())).thenReturn(revokedPayload);

        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest("signed-jws", "tx-4", null);
        StoreVerificationResult result = verifier.verify(userId, request);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isTransactionRevoked()).isTrue();
    }

    private JsonNode payloadNode(String bundleId, String productId, String txId, String appAccountToken) {
        return objectMapper.createObjectNode()
                .put("bundleId", bundleId)
                .put("productId", productId)
                .put("transactionId", txId)
                .put("appAccountToken", appAccountToken)
                .put("environment", "SANDBOX");
    }
}
