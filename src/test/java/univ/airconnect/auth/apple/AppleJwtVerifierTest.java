package univ.airconnect.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.service.oauth.apple.AppleAuthProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleJwtVerifierTest {

    @Mock
    private ApplePublicKeyProvider applePublicKeyProvider;

    private AppleAuthProperties appleAuthProperties;
    private AppleJwtVerifier appleJwtVerifier;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        appleAuthProperties = new AppleAuthProperties();
        appleAuthProperties.setBundleId("com.airconnect.app");
        appleAuthProperties.setServiceId("com.airconnect.web");
        appleAuthProperties.getLogin().setIssuer("https://appleid.apple.com");
        appleAuthProperties.getLogin().setAllowedAudiences(List.of("com.airconnect.app", "com.airconnect.web"));
        appleJwtVerifier = new AppleJwtVerifier(applePublicKeyProvider, appleAuthProperties);
        keyPair = generateRsaKeyPair();
    }

    @Test
    void verify_success_whenIssuerAndAudienceAreAllowed() {
        String token = createIdentityToken(
                keyPair.getPrivate(),
                "https://appleid.apple.com",
                "com.airconnect.app",
                "apple-user-1",
                "apple-user-1@privaterelay.appleid.com"
        );
        when(applePublicKeyProvider.getPublicKey(anyString())).thenReturn(keyPair.getPublic());

        Claims claims = appleJwtVerifier.verify(token);

        assertThat(claims.getSubject()).isEqualTo("apple-user-1");
        assertThat(claims.getAudience()).isEqualTo("com.airconnect.app");
    }

    @Test
    void verify_fails_whenAudienceMismatched() {
        String token = createIdentityToken(
                keyPair.getPrivate(),
                "https://appleid.apple.com",
                "com.untrusted.client",
                "apple-user-2",
                null
        );
        when(applePublicKeyProvider.getPublicKey(anyString())).thenReturn(keyPair.getPublic());

        assertThatThrownBy(() -> appleJwtVerifier.verify(token))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    void verify_fails_whenAllowedAudienceConfigMissing() {
        appleAuthProperties.getLogin().setAllowedAudiences(List.of());
        appleAuthProperties.setBundleId(null);
        appleAuthProperties.setServiceId(null);
        appleAuthProperties.setClientId(null);

        String token = createIdentityToken(
                keyPair.getPrivate(),
                "https://appleid.apple.com",
                "com.airconnect.app",
                "apple-user-3",
                null
        );
        when(applePublicKeyProvider.getPublicKey(anyString())).thenReturn(keyPair.getPublic());

        assertThatThrownBy(() -> appleJwtVerifier.verify(token))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_APPLE_TOKEN);
    }

    @Test
    void verify_fails_whenProdAudienceConfiguredButDevTokenArrives() {
        appleAuthProperties.getLogin().setAllowedAudiences(List.of("com.airconnect.app.prod"));

        String token = createIdentityToken(
                keyPair.getPrivate(),
                "https://appleid.apple.com",
                "com.airconnect.app.dev",
                "apple-user-4",
                null
        );
        when(applePublicKeyProvider.getPublicKey(anyString())).thenReturn(keyPair.getPublic());

        assertThatThrownBy(() -> appleJwtVerifier.verify(token))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_APPLE_TOKEN);
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String createIdentityToken(PrivateKey privateKey,
                                       String issuer,
                                       String audience,
                                       String subject,
                                       String email) {
        Instant now = Instant.now();
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setHeaderParam("kid", "test-kid")
                .setHeaderParam("alg", "RS256")
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(600)));
        if (email != null && !email.isBlank()) {
            builder.claim("email", email);
        }
        return builder.signWith(privateKey, SignatureAlgorithm.RS256).compact();
    }
}
