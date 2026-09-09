package univ.airconnect.global.security.stomp;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 현재 JVM에 연결된 STOMP 세션의 인증 사용자를 추적한다.
 *
 * <p>Redis 세션 정보는 운영 관측과 정리를 위한 메타데이터다. 보호된 outbound
 * 이벤트의 최종 허용 여부는 실제 로컬 WebSocket 세션 등록 상태를 기준으로 한다.</p>
 */
@Component
public class StompSessionRegistry {

    private final Map<String, Long> userIdBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        userIdBySessionId.put(sessionId, userId);
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            userIdBySessionId.remove(sessionId);
        }
    }

    public int revokeUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        int before = userIdBySessionId.size();
        userIdBySessionId.entrySet().removeIf(entry -> userId.equals(entry.getValue()));
        return before - userIdBySessionId.size();
    }

    public Optional<Long> findUserId(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userIdBySessionId.get(sessionId));
    }

    public boolean isAuthorized(String sessionId, Long userId) {
        return userId != null && userId.equals(userIdBySessionId.get(sessionId));
    }
}
