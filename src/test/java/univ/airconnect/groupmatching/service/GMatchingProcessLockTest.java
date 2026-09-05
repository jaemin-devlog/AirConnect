package univ.airconnect.groupmatching.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.global.config.RedisConfig;
import univ.airconnect.groupmatching.domain.GTeamSize;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Deterministic lock lifecycle model, not a real Redis/Lua integration test.
 * A virtual clock reproduces expiry without sleeps; script contract and command
 * serialization are checked separately. Redis atomicity is not proven by this mock.
 */
class GMatchingProcessLockTest {
    private static final String KEY = "matching:queue:process-lock:TWO";
    private static final String EXPECTED_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private RedisTemplate<String, Object> redis;
    private ValueOperations<String, Object> values;
    private GMatchingService service;
    private final Map<String, Lease> leases = new HashMap<>();
    private long nowMillis;
    private Runnable afterLegacyGet;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        service = mock(GMatchingService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenAnswer(call -> {
            String key = call.getArgument(0);
            if (current(key) != null) return false;
            Duration ttl = call.getArgument(2);
            leases.put(key, new Lease(call.getArgument(1), nowMillis + ttl.toMillis()));
            return true;
        });
        // Model the old commands too, so the GET/expiry/SET/DELETE regression
        // fails against the original implementation rather than an unstubbed mock.
        when(values.get(anyString())).thenAnswer(call -> {
            String token = current(call.getArgument(0));
            if (afterLegacyGet != null) {
                Runnable hook = afterLegacyGet;
                afterLegacyGet = null;
                hook.run();
            }
            return token;
        });
        when(redis.delete(anyString())).thenAnswer(call -> leases.remove(call.getArgument(0)) != null);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenAnswer(call -> {
            RedisScript<?> script = call.getArgument(0);
            assertThat(script.getScriptAsString()).isEqualTo(EXPECTED_SCRIPT);
            assertThat(script.getResultType()).isEqualTo(Long.class);
            List<String> keys = call.getArgument(1);
            assertThat(keys).hasSize(1);
            String token = call.getArgument(2);
            if (token.equals(current(keys.get(0)))) {
                leases.remove(keys.get(0));
                return 1L;
            }
            return 0L;
        });
    }

    @Test
    void ownTokenRelease_deletesLockUsingOneScript() {
        assertThat(acquire(KEY, "A")).isTrue();
        release(KEY, "A");
        assertThat(current(KEY)).isNull();
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq("A"));
        verify(values, never()).get(anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void foreignTokenRelease_preservesOwnerAndExpiry() {
        assertThat(acquire(KEY, "B")).isTrue();
        Lease original = leases.get(KEY);
        release(KEY, "A");
        assertThat(current(KEY)).isEqualTo("B");
        assertThat(leases.get(KEY)).isEqualTo(original);
    }

    @Test
    void expiredA_reacquiredB_lateARelease_preservesB() {
        assertThat(acquire(KEY, "A")).isTrue();
        nowMillis += 5_001;
        assertThat(current(KEY)).isNull();
        assertThat(acquire(KEY, "B")).isTrue();
        release(KEY, "A");
        assertThat(current(KEY)).isEqualTo("B");
        assertThat(acquire(KEY, "C")).isFalse();
    }

    @Test
    void release_hasNoClientGetDeleteWindowForReacquisition() {
        assertThat(acquire(KEY, "A")).isTrue();
        afterLegacyGet = () -> {
            nowMillis += 5_001;
            assertThat(acquire(KEY, "B")).isTrue();
        };
        release(KEY, "A");
        // Old GET triggers B's acquisition, then old DELETE removes B. The
        // atomic path completes first, so B can only acquire after release.
        if (afterLegacyGet != null) {
            afterLegacyGet = null;
            assertThat(acquire(KEY, "B")).isTrue();
        }
        assertThat(current(KEY)).isEqualTo("B");
    }

    @Test
    void duplicateRelease_isNoOp() {
        assertThat(acquire(KEY, "A")).isTrue();
        release(KEY, "A");
        release(KEY, "A");
        assertThat(current(KEY)).isNull();
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    @SuppressWarnings("unchecked")
    void processQueue_acquiresFiveSecondUuidLockAndReleasesOnEmptyQueue(GTeamSize size) {
        ListOperations<String, Object> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        when(lists.range("matching:queue:" + size.name(), 0, -1)).thenReturn(List.of());
        assertThat(service.processQueue(size, -1)).isNull();
        String key = "matching:queue:process-lock:" + size.name();
        var token = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(values).setIfAbsent(eq(key), token.capture(), eq(Duration.ofSeconds(5)));
        assertThat(UUID.fromString((String) token.getValue()).toString()).isEqualTo(token.getValue());
        verify(redis).execute(any(RedisScript.class), eq(List.of(key)), eq(token.getValue()));
        assertThat(current(key)).isNull();
    }

    @Test
    void busyLock_doesNotProcessQueueOrReleaseForeignLock() {
        assertThat(acquire(KEY, "B")).isTrue();
        assertThat(service.processQueue(GTeamSize.TWO, -1)).isNull();
        assertThat(current(KEY)).isEqualTo("B");
        verify(redis, never()).opsForList();
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void failingCriticalSection_stillReleasesOwnedLock() {
        when(redis.opsForList()).thenThrow(new IllegalStateException("queue unavailable"));
        assertThatThrownBy(() -> service.processQueue(GTeamSize.TWO, -1))
                .isInstanceOf(IllegalStateException.class).hasMessage("queue unavailable");
        assertThat(current(KEY)).isNull();
        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void productionRedisTemplate_serializesStringsAndHandlesLongWithScriptCacheFallback(boolean cached) {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        org.mockito.stubbing.Answer<Long> serializedCall = call -> {
            byte[][] args = (byte[][]) call.getRawArguments()[3];
            assertThat(args.length).isEqualTo(2);
            assertThat(args[0]).isEqualTo(KEY.getBytes(StandardCharsets.UTF_8));
            assertThat(args[1]).isEqualTo("token-한글".getBytes(StandardCharsets.UTF_8));
            return 1L;
        };
        if (cached) {
            when(connection.evalSha(anyString(), eq(ReturnType.INTEGER), eq(1), any(byte[][].class)))
                    .thenAnswer(serializedCall);
        } else {
            when(connection.evalSha(anyString(), eq(ReturnType.INTEGER), eq(1), any(byte[][].class)))
                    .thenThrow(new RedisSystemException("NOSCRIPT No matching script", null));
            when(connection.eval(any(byte[].class), eq(ReturnType.INTEGER), eq(1), any(byte[][].class)))
                    .thenAnswer(serializedCall);
        }
        RedisTemplate<String, Object> template = new RedisConfig().redisTemplate(factory);
        ReflectionTestUtils.setField(service, "redisTemplate", template);
        release(KEY, "token-한글");
        verify(connection).evalSha(anyString(), eq(ReturnType.INTEGER), eq(1), any(byte[][].class));
        verify(connection, times(cached ? 0 : 1)).eval(
                eq(EXPECTED_SCRIPT.getBytes(StandardCharsets.UTF_8)),
                eq(ReturnType.INTEGER), eq(1), any(byte[][].class));
    }

    private boolean acquire(String key, String token) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "tryAcquireProcessLock", key, token));
    }

    private void release(String key, String token) {
        ReflectionTestUtils.invokeMethod(service, "safelyReleaseProcessLock", key, token);
    }

    private String current(String key) {
        Lease lease = leases.get(key);
        if (lease != null && nowMillis >= lease.expiresAt()) {
            leases.remove(key);
            return null;
        }
        return lease == null ? null : lease.token();
    }

    private record Lease(String token, long expiresAt) {}
}
