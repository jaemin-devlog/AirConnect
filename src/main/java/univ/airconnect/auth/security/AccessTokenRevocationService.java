package univ.airconnect.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Stores only one-way fingerprints, expiring when the revoked access token expires. */
@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService {
    private static final String PREFIX = "auth:revoked-access:";
    private final StringRedisTemplate redisTemplate;

    public void revoke(String token, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (!remaining.isNegative() && !remaining.isZero()) {
            redisTemplate.opsForValue().set(PREFIX + fingerprint(token), "1", remaining);
        }
    }

    public boolean isRevokedFingerprint(String fingerprint) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + fingerprint));
    }

    public boolean isSessionActive(Long userId, String deviceId, String sessionId) {
        if (userId == null || deviceId == null || deviceId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Object activeSession = redisTemplate.opsForHash().get("refreshToken:" + userId + ":" + deviceId, "sessionId");
        return sessionId.equals(activeSession);
    }

    public static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
