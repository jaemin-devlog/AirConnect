package univ.airconnect.chat.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMessageThrottleServiceTest {

    @Test
    void allowsBoundaryThenRejectsOverQuota() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new ChatMessageThrottleService(redis, 120);
        when(redis.execute(any(RedisScript.class), eq(List.of("security:chat-send:10"))))
                .thenReturn(120L, 121L);

        assertThatCode(() -> limiter.checkSend(10L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkSend(10L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_RATE_LIMITED);
    }

    @Test
    void unavailableQuotaStoreFailsClosedWithoutLeakingItsError() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new ChatMessageThrottleService(redis, 120);
        when(redis.execute(any(RedisScript.class), eq(List.of("security:chat-send:10"))))
                .thenThrow(new IllegalStateException("private connection configuration"));

        assertThatThrownBy(() -> limiter.checkSend(10L)).isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("private connection configuration")
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_TEMPORARILY_UNAVAILABLE);
    }
}
