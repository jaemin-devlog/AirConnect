package univ.airconnect.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

@Component
@RequiredArgsConstructor
public class AppleJwtVerifier {

    private final ApplePublicKeyProvider applePublicKeyProvider;

    private static final String ISSUER = "https://appleid.apple.com";
    private static final String AUDIENCE = "com.jiin.AirConnect";

    public Claims verify(String identityToken) {

        PublicKey publicKey =
                applePublicKeyProvider.getPublicKey(identityToken);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(identityToken)
                .getBody();

        System.out.println("AUD = " + claims.getAudience());

        validateClaims(claims);

        return claims;
    }

    private void validateClaims(Claims claims) {

        if (!ISSUER.equals(claims.getIssuer())) {
            throw new RuntimeException("Invalid Apple issuer");
        }

        if (!AUDIENCE.equals(claims.getAudience())) {
            throw new RuntimeException("Invalid Apple audience");
        }
    }
}