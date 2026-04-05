package univ.airconnect.ads.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdmobSignatureVerifier {

    private final AdmobVerifierKeyProvider admobVerifierKeyProvider;

    public boolean verify(HttpServletRequest request) {
        try {
            String rawQuery = request.getQueryString();
            if (rawQuery == null || rawQuery.isBlank()) {
                return false;
            }

            String signatureValue = getRawQueryParam(rawQuery, "signature");
            String keyId = getRawQueryParam(rawQuery, "key_id");
            if (signatureValue.isBlank() || keyId.isBlank()) {
                return false;
            }

            String dataToVerify = extractDataToVerify(rawQuery);
            if (dataToVerify.isBlank()) {
                return false;
            }

            PublicKey publicKey = admobVerifierKeyProvider.getPublicKey(keyId);
            byte[] signatureBytes = decodeSignature(signatureValue);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(dataToVerify.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("AdMob signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private String extractDataToVerify(String rawQuery) {
        int signatureStartIndex = rawQuery.indexOf("&signature=");
        if (signatureStartIndex >= 0) {
            return rawQuery.substring(0, signatureStartIndex);
        }
        if (rawQuery.startsWith("signature=")) {
            return "";
        }
        return rawQuery;
    }

    private String getRawQueryParam(String rawQuery, String key) {
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            if (part.startsWith(key + "=")) {
                return part.substring((key + "=").length());
            }
        }
        return "";
    }

    private byte[] decodeSignature(String signatureValue) {
        try {
            return Base64.getUrlDecoder().decode(signatureValue);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(signatureValue);
        }
    }
}

