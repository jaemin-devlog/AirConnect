package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.global.tx.AfterCommitExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GRedisMatchingPushServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    void notifyMatched_publishesDispatchEventPerDevice() throws Exception {
        GRedisMatchingPushService service =
                new GRedisMatchingPushService(refreshTokenRepository, redisTemplate, objectMapper, new AfterCommitExecutor());

        when(refreshTokenRepository.findByUserId(10L))
                .thenReturn(List.of(
                        RefreshToken.create(10L, "ios-1", "rt-1"),
                        RefreshToken.create(10L, "android-1", "rt-2")
                ));
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{\"ok\":true}");

        service.notifyMatched(List.of(10L), 100L, 200L);

        verify(redisTemplate, times(2)).convertAndSend(eq("push:dispatch:matching"), eq("{\"ok\":true}"));
    }
}
