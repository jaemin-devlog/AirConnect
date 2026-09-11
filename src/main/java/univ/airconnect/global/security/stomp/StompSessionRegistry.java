package univ.airconnect.global.security.stomp;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

import java.util.HashSet;
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
public class StompSessionRegistry implements ApplicationEventPublisherAware {

    public static final String ONLINE_USERS_DESTINATION = "/sub/statistics/online";

    private final Map<String, Long> userIdBySessionId = new ConcurrentHashMap<>();
    private final Object mutationMonitor = new Object();
    private volatile ApplicationEventPublisher eventPublisher = event -> { };

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    public void register(String sessionId, Long userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null) {
            return;
        }
        Integer changedCount = null;
        synchronized (mutationMonitor) {
            int before = distinctUserCount();
            userIdBySessionId.put(sessionId, userId);
            int after = distinctUserCount();
            if (before != after) {
                changedCount = after;
            }
        }
        publishIfChanged(changedCount);
    }

    public void remove(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Integer changedCount = null;
        synchronized (mutationMonitor) {
            int before = distinctUserCount();
            userIdBySessionId.remove(sessionId);
            int after = distinctUserCount();
            if (before != after) {
                changedCount = after;
            }
        }
        publishIfChanged(changedCount);
    }

    public int revokeUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        int removedSessions;
        Integer changedCount = null;
        synchronized (mutationMonitor) {
            int beforeSessions = userIdBySessionId.size();
            int beforeUsers = distinctUserCount();
            userIdBySessionId.entrySet().removeIf(entry -> userId.equals(entry.getValue()));
            removedSessions = beforeSessions - userIdBySessionId.size();
            int afterUsers = distinctUserCount();
            if (beforeUsers != afterUsers) {
                changedCount = afterUsers;
            }
        }
        publishIfChanged(changedCount);
        return removedSessions;
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

    public int onlineUserCount() {
        synchronized (mutationMonitor) {
            return distinctUserCount();
        }
    }

    private int distinctUserCount() {
        return new HashSet<>(userIdBySessionId.values()).size();
    }

    private void publishIfChanged(Integer onlineUserCount) {
        if (onlineUserCount != null) {
            eventPublisher.publishEvent(OnlineUserCountChangedEvent.of(onlineUserCount));
        }
    }
}
