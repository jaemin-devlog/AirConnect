package univ.airconnect.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;

import java.security.PublicKey;

@Component
@RequiredArgsConstructor
public class AppleJwtVerifier {

    private final ApplePublicKeyProvider applePublicKeyProvider;

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String AUDIENCE = "com.jiin.AirConnect";

    public Claims verify(String identityToken) {
        try {
            PublicKey publicKey = applePublicKeyProvider.getPublicKey(identityToken);

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            validateClaims(claims);

            return claims;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    private void validateClaims(Claims claims) {
        if (!ISSUER.equals(claims.getIssuer())) {
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }

        if (!AUDIENCE.equals(claims.getAudience())) {
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }
}

