package univ.airconnect.iap.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.iap.apple.AppleSignedTransactionVerifier;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapEvent;
import univ.airconnect.iap.infrastructure.IapProperties;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;
import univ.airconnect.iap.repository.IapEventRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IapWebhookServiceTest {

    @Mock
    private IapEventRepository iapEventRepository;
    @Mock
    private AppleSignedTransactionVerifier appleSignedTransactionVerifier;
    @Mock
    private IapRefundService iapRefundService;

    @Test
    void ingestAppleNotification_refundsRevokedTransaction() {
        IapProperties iapProperties = new IapProperties();
        iapProperties.getApple().setBundleId("com.airconnect.app");
        IapWebhookService service = new IapWebhookService(
                iapEventRepository,
                new PayloadSecurityUtil(),
                new ObjectMapper(),
                appleSignedTransactionVerifier,
                iapRefundService,
                iapProperties
        );

        ObjectNode envelope = new ObjectMapper().createObjectNode()
                .put("notificationType", "REFUND")
                .set("data", new ObjectMapper().createObjectNode()
                        .put("bundleId", "com.airconnect.app")
                        .put("signedTransactionInfo", "signed-tx"));
        ObjectNode transaction = new ObjectMapper().createObjectNode()
                .put("transactionId", "2000001234567890")
                .put("revocationDate", "1730000000000");

        when(appleSignedTransactionVerifier.verifyAndExtractPayload("signed-envelope")).thenReturn(envelope);
        when(appleSignedTransactionVerifier.verifyAndExtractPayload("signed-tx")).thenReturn(transaction);
        when(iapEventRepository.save(any(IapEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.ingestAppleNotification(Map.of("signedPayload", "signed-envelope"));

        assertThat(response.isAccepted()).isTrue();
        verify(iapRefundService).refundAppleTransaction("2000001234567890", "apple_webhook:REFUND");
    }

    @Test
    void ingestGoogleNotification_decodesPubSubEnvelopeAndStoresEvent() throws Exception {
        IapWebhookService service = new IapWebhookService(
                iapEventRepository,
                new PayloadSecurityUtil(),
                new ObjectMapper(),
                appleSignedTransactionVerifier,
                iapRefundService,
                new IapProperties()
        );

        String encoded = java.util.Base64.getEncoder().encodeToString("""
                {"version":"1.0","packageName":"com.airconnect.app","oneTimeProductNotification":{"notificationType":2,"purchaseToken":"token-1","sku":"sku-1"}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(iapEventRepository.save(any(IapEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.ingestGoogleNotification(Map.of(
                "message", Map.of("data", encoded)
        ));

        assertThat(response.isAccepted()).isTrue();
        verify(iapEventRepository).save(any(IapEvent.class));
    }
}
