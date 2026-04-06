package univ.airconnect.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.service.oauth.apple.AppleAuthProperties;

import java.security.PublicKey;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AppleJwtVerifier {

    private final ApplePublicKeyProvider applePublicKeyProvider;
    private final AppleAuthProperties appleAuthProperties;

    public Claims verify(String identityToken) {
        try {
            validateIdentityTokenInput(identityToken);
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
        } catch (IllegalStateException e) {
            log.warn("Apple login verify config/state error. reason={}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        } catch (Exception e) {
            log.warn("Apple login verify failed. reason={}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    private void validateClaims(Claims claims) {
        String expectedIssuer = appleAuthProperties.resolveLoginIssuer();
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            throw new IllegalStateException("Missing required property: apple.login.issuer");
        }

        List<String> allowedAudiences = appleAuthProperties.resolveAllowedAudiences();
        if (allowedAudiences.isEmpty()) {
            throw new IllegalStateException("Missing required property: apple.login.allowed-audiences");
        }

        if (!expectedIssuer.equals(claims.getIssuer())) {
            log.warn("Apple login issuer mismatch. expectedIssuer={}, actualIssuer={}",
                    expectedIssuer, mask(claims.getIssuer()));
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }

        String audience = claims.getAudience();
        if (audience == null || audience.isBlank()) {
            log.warn("Apple login audience is missing in token.");
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }

        if (!allowedAudiences.contains(audience)) {
            log.warn("Apple login audience mismatch. allowedAudiences={}, actualAudience={}",
                    maskAudiences(allowedAudiences), mask(audience));
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            log.warn("Apple login subject is missing.");
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    private void validateIdentityTokenInput(String identityToken) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    private String maskAudiences(List<String> audiences) {
        return audiences.stream()
                .map(this::mask)
                .reduce((first, second) -> first + "," + second)
                .orElse("-");
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "..." + value.substring(value.length() - 3);
    }
}
