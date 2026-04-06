package univ.airconnect.auth.service.oauth.apple;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class AppleClientSecretService {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleAuthProperties appleAuthProperties;
    private final ResourceLoader resourceLoader;

    private volatile ECPrivateKey cachedPrivateKey;

    public String createClientSecret() {
        String teamId = requireValue(appleAuthProperties.getTeamId(), "apple.team-id");
        String keyId = requireValue(appleAuthProperties.getKeyId(), "apple.key-id");
        String clientId = requireValue(appleAuthProperties.resolveRevokeClientId(), "apple.revoke.client-id");
        long ttlSeconds = appleAuthProperties.getRevoke().getClientSecretTtlSeconds();
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("apple.revoke.client-secret-ttl-seconds must be positive");
        }

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .setHeaderParam("kid", keyId)
                .setIssuer(teamId)
                .setAudience(APPLE_ISSUER)
                .setSubject(clientId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(loadPrivateKey(), SignatureAlgorithm.ES256)
                .compact();
    }

    private ECPrivateKey loadPrivateKey() {
        ECPrivateKey local = cachedPrivateKey;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (cachedPrivateKey != null) {
                return cachedPrivateKey;
            }

            String keyPath = requireValue(appleAuthProperties.resolvePrivateKeyPath(), "apple.private-key-path");
            try {
                Resource resource = resourceLoader.getResource(keyPath);
                if (!resource.exists()) {
                    throw new IllegalStateException("Apple private key file not found: " + keyPath);
                }

                String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String normalized = pem
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] keyBytes = Base64.getDecoder().decode(normalized);

                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
                if (!(privateKey instanceof ECPrivateKey ecPrivateKey)) {
                    throw new IllegalStateException("Apple private key is not EC key");
                }

                cachedPrivateKey = ecPrivateKey;
                return ecPrivateKey;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load Apple private key", e);
            }
        }
    }

    private String requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + propertyName);
        }
        return value;
    }
}
