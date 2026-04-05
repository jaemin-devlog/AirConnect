package univ.airconnect.ads.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdmobSignatureVerifier {

    private static final String SIGNATURE_PARAM_NAME = "signature=";
    private static final String KEY_ID_PARAM_NAME = "key_id=";

    private final AdmobVerifierKeyProvider admobVerifierKeyProvider;

    public boolean verify(HttpServletRequest request) {
        try {
            String rawQuery = request.getQueryString();
            if (rawQuery == null || rawQuery.isBlank()) {
                return false;
            }

            int sigStart = rawQuery.indexOf(SIGNATURE_PARAM_NAME);
            if (sigStart == -1) {
                log.warn("AdMob signature verification failed: missing signature param");
                return false;
            }

            // Google 문서 기준:
            // signature 앞까지의 원본 query string을 '그대로' 검증해야 한다.
            // signature 앞에는 항상 '&'가 있으므로 sigStart - 1 까지만 사용한다.
            if (sigStart == 0) {
                log.warn("AdMob signature verification failed: signature is first query param");
                return false;
            }

            String dataToVerify = rawQuery.substring(0, sigStart - 1);

            String sigAndKeyId = rawQuery.substring(sigStart);
            int keyIdStart = sigAndKeyId.indexOf(KEY_ID_PARAM_NAME);
            if (keyIdStart == -1) {
                log.warn("AdMob signature verification failed: missing key_id param");
                return false;
            }

            // "signature=<값>&key_id=<값>" 구조
            String signatureValue = sigAndKeyId.substring(
                    SIGNATURE_PARAM_NAME.length(),
                    keyIdStart - 1
            );
            String keyId = sigAndKeyId.substring(keyIdStart + KEY_ID_PARAM_NAME.length());

            if (signatureValue.isBlank() || keyId.isBlank()) {
                return false;
            }

            PublicKey publicKey = admobVerifierKeyProvider.getPublicKey(keyId);
            byte[] signatureBytes = decodeSignature(signatureValue);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(dataToVerify.getBytes(StandardCharsets.UTF_8));

            boolean verified = verifier.verify(signatureBytes);

            if (!verified) {
                log.warn("AdMob signature verification failed: verify returned false. keyId={}, dataLength={}",
                        keyId, dataToVerify.length());
            }

            return verified;
        } catch (Exception e) {
            log.warn("AdMob signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }

    private byte[] decodeSignature(String signatureValue) {
        // signature는 URL에 실려 오므로 percent-decoding만 먼저 수행
        String decoded = urlDecode(signatureValue);

        try {
            // AdMob 예시는 URL-safe base64를 사용
            return Base64.getUrlDecoder().decode(decoded);
        } catch (IllegalArgumentException ignored) {
            // 혹시 일반 base64 형태로 오면 fallback
            return Base64.getDecoder().decode(decoded);
        }
    }

    private String urlDecode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}