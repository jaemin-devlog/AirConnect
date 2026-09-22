package univ.airconnect.auth.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessTokenRevocationServiceTest {
    @Test
    void revocationStoresOnlyFingerprintUntilTokenExpires() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AccessTokenRevocationService service = new AccessTokenRevocationService(redis);
        String token = "dummy.signed.access-token";

        service.revoke(token, Instant.now().plusSeconds(120));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(key.capture(), eq("1"), ttl.capture());
        assertThat(key.getValue()).doesNotContain(token).endsWith(AccessTokenRevocationService.fingerprint(token));
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void expiredTokensNeedNoPersistentRevocationEntry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        new AccessTokenRevocationService(redis).revoke("expired", Instant.now().minusSeconds(1));
        verify(redis, never()).opsForValue();
    }

    @Test
    void sessionMustStillExistAndMatchCurrentLogin() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.get("refreshToken:15:device-a", "sessionId")).thenReturn("new-session", "new-session", null);
        AccessTokenRevocationService service = new AccessTokenRevocationService(redis);

        assertThat(service.isSessionActive(15L, "device-a", "old-session")).isFalse();
        assertThat(service.isSessionActive(15L, "device-a", "new-session")).isTrue();
        assertThat(service.isSessionActive(15L, "device-a", "new-session")).isFalse();
        assertThat(service.isSessionActive(15L, null, "new-session")).isFalse();
    }
}
