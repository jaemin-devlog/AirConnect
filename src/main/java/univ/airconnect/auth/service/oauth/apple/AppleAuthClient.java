package univ.airconnect.auth.service.oauth.apple;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import univ.airconnect.auth.apple.AppleJwtVerifier;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.service.oauth.SocialAuthClient;

/**
 * Apple 소셜 로그인을 위한 클라이언트
 * identityToken을 검증하고 Apple 사용자 ID를 추출한다.
 */
@Component
@RequiredArgsConstructor
public class AppleAuthClient implements SocialAuthClient {

    private final AppleJwtVerifier appleJwtVerifier;

    @Override
    public SocialProvider supports() {
        return SocialProvider.APPLE;
    }

    @Override
    public String getSocialId(String identityToken) {
        // identityToken = Apple에서 받은 JWT
        Claims claims = appleJwtVerifier.verify(identityToken);
        return claims.getSubject();
    }

    /**
     * Apple identityToken에서 이메일을 추출한다.
     * (소셜 로그인 시 email 정보 저장을 위해 사용)
     */
    public String getEmail(String identityToken) {
        Claims claims = appleJwtVerifier.verify(identityToken);
        return claims.get("email", String.class);
    }
}

