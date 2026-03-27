package univ.airconnect.auth.domain.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

/**
 * 발급된 RefreshToken을 Redis에 저장하기 위한 엔티티.
 * userId + deviceId 기반으로 멀티 디바이스 토큰을 관리한다.
 */
@Getter
@Builder
@RedisHash("refreshToken")
public class RefreshToken {

    /** RefreshToken 만료 시간(30일) */
    public static final long TTL_SECONDS = 60 * 60 * 24 * 30;

    /**
     * Redis Key(PK).
     * "{userId}:{deviceId}" 형태로 생성한다.
     */
    @Id
    private String id;

    /** AirConnect 사용자 PK */
    private Long userId;

    /** 클라이언트 기기 식별자 */
    private String deviceId;

    /** 실제 RefreshToken 문자열 */
    private String token;

    /**
     * Redis TTL (초 단위).
     * 만료 시 Redis에서 자동 삭제된다.
     */
    @TimeToLive
    private Long ttlSeconds;

    /**
     * RefreshToken 생성 팩토리 메서드
     */
    public static RefreshToken create(Long userId, String deviceId, String token) {
        return RefreshToken.builder()
                .id(generateId(userId, deviceId))
                .userId(userId)
                .deviceId(deviceId)
                .token(token)
                .ttlSeconds(TTL_SECONDS)
                .build();
    }

    /** Redis Key 생성 규칙 */
    private static String generateId(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }
}
