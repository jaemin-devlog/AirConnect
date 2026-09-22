package univ.airconnect.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import univ.airconnect.auth.domain.entity.RefreshToken;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Includes already issued tokens, which have no userId secondary index in Redis. */
@RequiredArgsConstructor
public class RefreshTokenUserLookupImpl implements RefreshTokenUserLookup {
    private final StringRedisTemplate redisTemplate;

    @Override
    public Iterable<RefreshToken> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Map<String, RefreshToken> tokens = new LinkedHashMap<>();
        ScanOptions options = ScanOptions.scanOptions().match("refreshToken:" + userId + ":*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
                String id = (String) fields.get("id");
                String deviceId = (String) fields.get("deviceId");
                String token = (String) fields.get("token");
                // Ignore expired hashes and Spring Data's phantom copies.
                if (id != null && deviceId != null && token != null
                        && key.equals("refreshToken:" + id)
                        && String.valueOf(userId).equals(fields.get("userId"))) {
                    tokens.put(id, RefreshToken.create(userId, deviceId, token, (String) fields.get("sessionId")));
                }
            }
        }
        return List.copyOf(tokens.values());
    }
}
