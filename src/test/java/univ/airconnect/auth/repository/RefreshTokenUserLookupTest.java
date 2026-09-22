package univ.airconnect.auth.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import univ.airconnect.auth.domain.entity.RefreshToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenUserLookupTest {
    @Test
    void findsUnindexedLegacySessionsButNotExpiredShadowCopiesOrAnotherUser() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        Cursor<String> cursor = mock(Cursor.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, true, true, true, false);
        when(cursor.next()).thenReturn("refreshToken:15:device-a", "refreshToken:15:device-b",
                "refreshToken:15:device-b:phantom", "refreshToken:15:expired", "refreshToken:15:wrong-user");
        when(hashes.entries("refreshToken:15:device-a")).thenReturn(fields("15:device-a", "15", "device-a"));
        when(hashes.entries("refreshToken:15:device-b")).thenReturn(fields("15:device-b", "15", "device-b"));
        when(hashes.entries("refreshToken:15:device-b:phantom")).thenReturn(fields("15:device-b", "15", "device-b"));
        when(hashes.entries("refreshToken:15:expired")).thenReturn(Map.of());
        when(hashes.entries("refreshToken:15:wrong-user")).thenReturn(fields("15:wrong-user", "99", "wrong-user"));

        Iterable<RefreshToken> result = new RefreshTokenUserLookupImpl(redis).findByUserId(15L);

        assertThat(result).extracting(RefreshToken::getId).containsExactly("15:device-a", "15:device-b");
        verify(cursor).close();
    }

    private Map<Object, Object> fields(String id, String userId, String deviceId) {
        return Map.of("id", id, "userId", userId, "deviceId", deviceId, "token", "dummy-token-hash");
    }
}
