package univ.airconnect.ads.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdmobSignatureVerifierTest {

    @Mock
    private AdmobVerifierKeyProvider admobVerifierKeyProvider;

    @Test
    void verify_returnsTrue_whenSignatureMatchesRawQueryBeforeSignatureParam() throws Exception {
        KeyPair keyPair = generateEcKeyPair();

        String dataToVerify = "ad_network=5450213213286189855&ad_unit=test_unit&custom_data=sessionKey%3Dabc123&transaction_id=tx-001";
        String signature = sign(dataToVerify, keyPair);
        String query = dataToVerify + "&signature=" + signature + "&key_id=42";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString(query);

        when(admobVerifierKeyProvider.getPublicKey("42")).thenReturn(keyPair.getPublic());

        AdmobSignatureVerifier verifier = new AdmobSignatureVerifier(admobVerifierKeyProvider);

        assertThat(verifier.verify(request)).isTrue();
    }

    @Test
    void verify_returnsFalse_whenRawQueryGetsMutated() throws Exception {
        KeyPair keyPair = generateEcKeyPair();

        String original = "custom_data=sessionKey%3Dabc123&transaction_id=tx-001";
        String signature = sign(original, keyPair);
        String mutated = "custom_data=sessionKey=abc123&transaction_id=tx-001";
        String query = mutated + "&signature=" + signature + "&key_id=42";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString(query);

        when(admobVerifierKeyProvider.getPublicKey("42")).thenReturn(keyPair.getPublic());

        AdmobSignatureVerifier verifier = new AdmobSignatureVerifier(admobVerifierKeyProvider);

        assertThat(verifier.verify(request)).isFalse();
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        return keyPairGenerator.generateKeyPair();
    }

    private String sign(String data, KeyPair keyPair) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
}

