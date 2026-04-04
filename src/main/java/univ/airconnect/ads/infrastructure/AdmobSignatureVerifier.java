package univ.airconnect.ads.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AdmobSignatureVerifier {

    public boolean verify(HttpServletRequest request) {
        // TODO: AdMob SSV signature 검증(public key fetch + canonical string verify)을 여기에 구현.
        // 현재는 구조 제공 단계로, signature 파라미터 존재 여부를 최소 검증으로 사용한다.
        String signature = request.getParameter("signature");
        String keyId = request.getParameter("key_id");
        return signature != null && !signature.isBlank() && keyId != null && !keyId.isBlank();
    }
}

