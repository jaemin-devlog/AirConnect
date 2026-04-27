package univ.airconnect.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AttemptThrottleService {

    private static final String PREFIX = "security_attempt";

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String scope, String identifier, String clientIp) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(scope, identifier, clientIp)));
    }

    public boolean recordFailure(
            String scope,
            String identifier,
            String clientIp,
            int maxAttempts,
            long counterTtlSeconds,
            long lockTtlSeconds
    ) {
        String counterKey = counterKey(scope, identifier, clientIp);
        Long attempts = redisTemplate.opsForValue().increment(counterKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(counterKey, counterTtlSeconds, TimeUnit.SECONDS);
        }

        if (attempts != null && attempts >= maxAttempts) {
            redisTemplate.opsForValue().set(lockKey(scope, identifier, clientIp), "1", lockTtlSeconds, TimeUnit.SECONDS);
            redisTemplate.delete(counterKey);
            return true;
        }

        return false;
    }

    public void clear(String scope, String identifier, String clientIp) {
        redisTemplate.delete(counterKey(scope, identifier, clientIp));
        redisTemplate.delete(lockKey(scope, identifier, clientIp));
    }

    private String counterKey(String scope, String identifier, String clientIp) {
        return PREFIX + ":" + scope + ":counter:" + encode(identifier) + ":" + encode(clientIp);
    }

    private String lockKey(String scope, String identifier, String clientIp) {
        return PREFIX + ":" + scope + ":lock:" + encode(identifier) + ":" + encode(clientIp);
    }

    private String encode(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
