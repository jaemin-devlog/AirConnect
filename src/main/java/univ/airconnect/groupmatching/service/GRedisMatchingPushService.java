package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.global.tx.AfterCommitExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class GRedisMatchingPushService implements GMatchingPushService {

    private static final String PUSH_DISPATCH_CHANNEL = "push:dispatch:matching";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AfterCommitExecutor afterCommitExecutor;

    @Override
    public void notifyMatched(Collection<Long> userIds, Long finalGroupRoomId, Long finalChatRoomId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LinkedHashSet<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        afterCommitExecutor.execute(() -> notifyMatchedImmediately(distinctUserIds, finalGroupRoomId, finalChatRoomId));
    }

    private void notifyMatchedImmediately(Collection<Long> userIds, Long finalGroupRoomId, Long finalChatRoomId) {
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            Iterable<RefreshToken> refreshTokens = refreshTokenRepository.findByUserId(userId);
            for (RefreshToken refreshToken : refreshTokens) {
                if (refreshToken == null || refreshToken.getDeviceId() == null || refreshToken.getDeviceId().isBlank()) {
                    continue;
                }
                publishDispatchEvent(userId, refreshToken.getDeviceId(), finalGroupRoomId, finalChatRoomId);
            }
        }
    }

    private void publishDispatchEvent(Long userId, String deviceId, Long finalGroupRoomId, Long finalChatRoomId) {
        PushDispatchPayload payload = new PushDispatchPayload(
                "MATCHED",
                userId,
                deviceId,
                finalGroupRoomId,
                finalChatRoomId,
                LocalDateTime.now()
        );

        try {
            redisTemplate.convertAndSend(PUSH_DISPATCH_CHANNEL, objectMapper.writeValueAsString(payload));
            log.info("과팅 푸시 디스패치 이벤트를 발행했습니다. userId={}, deviceId={}, finalChatRoomId={}",
                    userId, deviceId, finalChatRoomId);
        } catch (JsonProcessingException e) {
            log.error("과팅 푸시 디스패치 직렬화에 실패했습니다. userId={}, deviceId={}", userId, deviceId, e);
        }
    }

    private record PushDispatchPayload(
            String type,
            Long userId,
            String deviceId,
            Long finalGroupRoomId,
            Long finalChatRoomId,
            LocalDateTime occurredAt
    ) {
    }
}
