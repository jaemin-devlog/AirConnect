package univ.airconnect.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.domain.entity.RefreshToken;

import java.util.List;

/** Atomically rotates the existing Spring Data Redis hash without recreating a logged-out session. */
@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {

    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'token', ARGV[2], 'ttlSeconds', ARGV[3], 'sessionId', ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('HSET', KEYS[2], 'token', ARGV[2], 'ttlSeconds', ARGV[3], 'sessionId', ARGV[4])
                redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]) + 300)
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean rotate(String id, String expectedStoredToken, String nextTokenHash, String sessionId) {
        String key = "refreshToken:" + id;
        return Long.valueOf(1).equals(redisTemplate.execute(ROTATE,
                List.of(key, key + ":phantom"), expectedStoredToken, nextTokenHash,
                String.valueOf(RefreshToken.TTL_SECONDS), sessionId));
    }
}
