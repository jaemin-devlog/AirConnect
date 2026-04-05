package univ.airconnect.ads.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
            String keyId = decodeValue(getRawQueryParam(rawQuery, "key_id"));
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
        String[] parts = rawQuery.split("&");
        List<String> signedParts = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.startsWith("signature=")) {
                break;
            }
            signedParts.add(part);
        }

        if (signedParts.isEmpty()) {
            return "";
        }
        return String.join("&", signedParts);
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
        String decoded = decodeValue(signatureValue);
        try {
            return Base64.getUrlDecoder().decode(decoded);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(decoded);
        }
    }

    private String decodeValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            // Query string values may be URL-encoded in callback delivery.
            return URLDecoder.decode(value, StandardCharsets.UTF_8).replace(" ", "+");
        } catch (Exception ignored) {
            return value;
        }
    }
}

