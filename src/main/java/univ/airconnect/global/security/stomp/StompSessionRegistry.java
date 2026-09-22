package univ.airconnect.global.security.stomp;

import org.springframework.stereotype.Component;
import univ.airconnect.auth.security.AccessTokenRevocationService;

import java.time.Instant;
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

    public static final String ONLINE_USERS_DESTINATION = "/sub/statistics/online";
    public static final String MAIN_STATISTICS_DESTINATION = "/sub/statistics/main";
    public static final String DEPARTMENT_RANKINGS_DESTINATION = "/sub/statistics/departments/rankings";

    private final Map<String, SessionAuthorization> userIdBySessionId = new ConcurrentHashMap<>();
    private final Object mutationMonitor = new Object();
    private final AccessTokenRevocationService accessTokenRevocationService;

    public StompSessionRegistry(AccessTokenRevocationService accessTokenRevocationService) {
        this.accessTokenRevocationService = accessTokenRevocationService;
    }

    public void register(String sessionId, Long userId, Instant expiresAt, String tokenFingerprint) {
        register(sessionId, userId, expiresAt, tokenFingerprint, null, null);
    }

    public void register(String sessionId, Long userId, Instant expiresAt, String tokenFingerprint,
                         String deviceId, String authSessionId) {
        if (sessionId == null || sessionId.isBlank() || userId == null || expiresAt == null
                || !expiresAt.isAfter(Instant.now()) || tokenFingerprint == null || tokenFingerprint.isBlank()
                || (authSessionId != null && (authSessionId.isBlank() || deviceId == null || deviceId.isBlank()))) {
            return;
        }
        synchronized (mutationMonitor) {
            userIdBySessionId.put(sessionId,
                    new SessionAuthorization(userId, expiresAt, tokenFingerprint, deviceId, authSessionId));
        }
    }

    public void remove(String sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (mutationMonitor) {
            userIdBySessionId.remove(sessionId);
        }
    }

    public int revokeUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        int removedSessions;
        synchronized (mutationMonitor) {
            int beforeSessions = userIdBySessionId.size();
            userIdBySessionId.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId()));
            removedSessions = beforeSessions - userIdBySessionId.size();
        }
        return removedSessions;
    }

    public Optional<Long> findUserId(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        SessionAuthorization session = userIdBySessionId.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.expiresAt().isAfter(Instant.now())) {
            userIdBySessionId.remove(sessionId, session);
            return Optional.empty();
        }
        try {
            if (accessTokenRevocationService.isRevokedFingerprint(session.tokenFingerprint())
                    || (session.authSessionId() != null && !accessTokenRevocationService.isSessionActive(
                    session.userId(), session.deviceId(), session.authSessionId()))) {
                userIdBySessionId.remove(sessionId, session);
                return Optional.empty();
            }
        } catch (RuntimeException ex) {
            // Redis failure must not turn a revoked socket into an authorized socket.
            return Optional.empty();
        }
        return Optional.of(session.userId());
    }

    public boolean isAuthorized(String sessionId, Long userId) {
        return userId != null && findUserId(sessionId).filter(userId::equals).isPresent();
    }

    public int onlineUserCount() {
        synchronized (mutationMonitor) {
            return distinctUserCount();
        }
    }

    private int distinctUserCount() {
        userIdBySessionId.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(Instant.now()));
        return (int) userIdBySessionId.values().stream().map(SessionAuthorization::userId).distinct().count();
    }

    private record SessionAuthorization(Long userId, Instant expiresAt, String tokenFingerprint,
                                        String deviceId, String authSessionId) { }
}
