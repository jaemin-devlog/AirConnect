package univ.airconnect.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private static final Duration ACTIVITY_TOUCH_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            Boolean firstTouch = redisTemplate.opsForValue()
                    .setIfAbsent(activityTouchKey(userId), "1", ACTIVITY_TOUCH_TTL);

            if (!Boolean.TRUE.equals(firstTouch)) {
                return;
            }

            userRepository.findById(userId).ifPresent(User::markActive);
        } catch (Exception e) {
            log.warn("사용자 활동 시각 갱신을 건너뜁니다. userId={}, reason={}", userId, e.getMessage());
        }
    }

    private String activityTouchKey(Long userId) {
        return "analytics:user:last-active:" + userId;
    }
}
