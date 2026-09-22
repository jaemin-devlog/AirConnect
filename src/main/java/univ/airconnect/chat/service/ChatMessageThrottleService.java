package univ.airconnect.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

import java.util.List;

/** REST and STOMP share the same user quota, including concurrent app instances. */
@Service
public class ChatMessageThrottleService {

    private static final DefaultRedisScript<Long> CHECK_SEND = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], 60000) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maxPerMinute;

    public ChatMessageThrottleService(StringRedisTemplate redis,
            @Value("${app.chat.max-messages-per-minute:120}") int maxPerMinute) {
        this.redis = redis;
        this.maxPerMinute = Math.max(1, maxPerMinute);
    }

    public void checkSend(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long count;
        try {
            count = redis.execute(CHECK_SEND, List.of("security:chat-send:" + userId));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.CHAT_TEMPORARILY_UNAVAILABLE);
        }
        if (count == null) {
            throw new BusinessException(ErrorCode.CHAT_TEMPORARILY_UNAVAILABLE);
        }
        if (count > maxPerMinute) {
            throw new BusinessException(ErrorCode.CHAT_RATE_LIMITED);
        }
    }
}
