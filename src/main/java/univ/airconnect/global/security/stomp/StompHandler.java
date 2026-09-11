package univ.airconnect.global.security.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.security.Principal;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private static final String CHAT_SEND_DESTINATION = "/pub/chat/message";
    private static final Pattern CHAT_ROOM_SUB_DESTINATION = Pattern.compile("^/sub/chat/room/([1-9]\\d*)$");
    private static final Pattern CHAT_LIST_SUB_DESTINATION = Pattern.compile("^/sub/chat/list/([1-9]\\d*)$");
    private static final Pattern MATCHING_TEAM_ROOM_SUB_DESTINATION = Pattern.compile("^/sub/matching/team-room/([1-9]\\d*)$");
    private static final String ONLINE_USERS_SUB_DESTINATION = StompSessionRegistry.ONLINE_USERS_DESTINATION;
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(CHAT_SEND_DESTINATION);

    private final JwtProvider jwtProvider;
    private final ChatService chatService;
    private final GMatchingService matchingService;
    private final StompOpsMonitor stompOpsMonitor;
    private final UserRepository userRepository;
    private final StompSessionRegistry sessionRegistry;

    public StompHandler(
            JwtProvider jwtProvider,
            @Lazy ChatService chatService,
            @Lazy GMatchingService matchingService,
            StompOpsMonitor stompOpsMonitor,
            UserRepository userRepository,
            StompSessionRegistry sessionRegistry
    ) {
        this.jwtProvider = jwtProvider;
        this.chatService = chatService;
        this.matchingService = matchingService;
        this.stompOpsMonitor = stompOpsMonitor;
        this.userRepository = userRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        stompOpsMonitor.recordInboundCommand(command);

        try {
            if (StompCommand.CONNECT.equals(command)) {
                handleConnectWithLogging(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                handleSubscribe(accessor);
            } else if (StompCommand.SEND.equals(command)) {
                handleSend(accessor);
            } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
                handleUnsubscribe(accessor);
            } else if (StompCommand.DISCONNECT.equals(command)) {
                handleDisconnect(accessor);
            }
        } catch (RuntimeException ex) {
            stompOpsMonitor.recordInboundFailure(command, ex);
            log.warn("STOMP INBOUND FAIL: command={}, sessionId={}, type={}, message={}",
                    command,
                    accessor.getSessionId(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            throw ex;
        }

        return message;
    }

    private void handleConnectWithLogging(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        String tokenSubject = extractSubjectForLog(token);

        log.debug("STOMP CONNECT INBOUND: sessionId={}, subject={}, headerKey={}",
                accessor.getSessionId(), tokenSubject, resolveAuthHeaderKey(accessor));

        try {
            jwtProvider.validateAccessToken(token);
            Long userId = jwtProvider.getUserId(token);
            User user = ensureActiveUser(userId);

            CustomUserPrincipal principal = new CustomUserPrincipal(userId, user.getRole());
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            accessor.setUser(authentication);
            sessionRegistry.register(accessor.getSessionId(), userId);
            try {
                // principal 정보가 이미 세팅되어 있으므로 Redis 세션 저장 실패로 CONNECT를 깨지 않는다.
                chatService.saveSessionInfo(accessor.getSessionId(), userId);
            } catch (RuntimeException redisEx) {
                stompOpsMonitor.recordSideEffectFailure("CONNECT_SAVE_SESSION", redisEx);
                log.warn("STOMP CONNECT SIDE-EFFECT FAIL: sessionId={}, userId={}, type={}, message={}",
                        accessor.getSessionId(),
                        userId,
                        redisEx.getClass().getSimpleName(),
                        redisEx.getMessage());
            }

            stompOpsMonitor.recordConnectSuccess();

            log.debug("STOMP CONNECT SUCCESS: sessionId={}, subject={}, userId={}",
                    accessor.getSessionId(), tokenSubject, userId);
        } catch (Exception e) {
            stompOpsMonitor.recordConnectFailure(e);
            log.warn("STOMP CONNECT FAIL: sessionId={}, subject={}, exceptionType={}, message={}",
                    accessor.getSessionId(), tokenSubject, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return;
        }

        Long userId = extractUserId(accessor);
        if (userId == null) {
            log.error("STOMP SUBSCRIBE REJECTED: sessionId={}, destination={}, reason=unauthenticated",
                    accessor.getSessionId(), destination);
            stompOpsMonitor.recordSubscribeFailure(new AccessDeniedException("unauthenticated"));
            throw new AccessDeniedException("Unable to resolve authenticated user.");
        }
        ensureActiveUser(userId);
        ensureAuthorizedSession(accessor, userId);

        Matcher chatRoomMatcher = CHAT_ROOM_SUB_DESTINATION.matcher(destination);
        if (chatRoomMatcher.matches()) {
            handleChatSubscribe(accessor, Long.valueOf(chatRoomMatcher.group(1)), userId);
            return;
        }

        Matcher chatListMatcher = CHAT_LIST_SUB_DESTINATION.matcher(destination);
        if (chatListMatcher.matches()) {
            handleChatListSubscribe(Long.valueOf(chatListMatcher.group(1)), userId);
            return;
        }

        Matcher matchingRoomMatcher = MATCHING_TEAM_ROOM_SUB_DESTINATION.matcher(destination);
        if (matchingRoomMatcher.matches()) {
            handleMatchingSubscribe(accessor, Long.valueOf(matchingRoomMatcher.group(1)), userId);
            return;
        }

        if (ONLINE_USERS_SUB_DESTINATION.equals(destination)) {
            stompOpsMonitor.recordSubscribeSuccess();
            return;
        }

        throw new AccessDeniedException("Unsupported STOMP subscription destination.");
    }

    private void handleChatSubscribe(StompHeaderAccessor accessor, Long roomId, Long userId) {
        if (!chatService.isMember(roomId, userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: roomId={}, userId={}, reason=not_member", roomId, userId);
            stompOpsMonitor.recordSubscribeFailure(new AccessDeniedException("not_member"));
            throw new AccessDeniedException("No permission to subscribe this chat room.");
        }

        // 구독 자체 권한은 이미 검증되었으므로, 부가 동기화 실패는 구독까지 차단하지 않는다.
        try {
            chatService.enterChatRoom(roomId.toString());
            chatService.registerSessionRoomSubscription(
                    accessor.getSessionId(),
                    accessor.getSubscriptionId(),
                    roomId.toString()
            );
        } catch (RuntimeException ex) {
            stompOpsMonitor.recordSideEffectFailure("SUBSCRIBE_REGISTRATION", ex);
            log.warn("STOMP SUBSCRIBE SIDE-EFFECT FAIL: sessionId={}, userId={}, roomId={}, type={}, message={}",
                    accessor.getSessionId(),
                    userId,
                    roomId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }

        stompOpsMonitor.recordSubscribeSuccess();

        log.debug("STOMP SUBSCRIBE CHAT: sessionId={}, userId={}, roomId={}",
                accessor.getSessionId(), userId, roomId);
    }

    private void handleMatchingSubscribe(StompHeaderAccessor accessor, Long teamRoomId, Long userId) {
        if (!matchingService.canSubscribeTeamRoom(teamRoomId, userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: teamRoomId={}, userId={}, reason=no_access", teamRoomId, userId);
            stompOpsMonitor.recordSubscribeFailure(new AccessDeniedException("no_access"));
            throw new AccessDeniedException("No permission to subscribe this matching room.");
        }

        stompOpsMonitor.recordSubscribeSuccess();

        log.debug("STOMP SUBSCRIBE MATCHING: sessionId={}, userId={}, teamRoomId={}",
                accessor.getSessionId(), userId, teamRoomId);
    }

    private void handleChatListSubscribe(Long subscribedUserId, Long userId) {
        if (!subscribedUserId.equals(userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: subscribedUserId={}, userId={}, reason=list_forbidden",
                    subscribedUserId, userId);
            stompOpsMonitor.recordSubscribeFailure(new AccessDeniedException("list_forbidden"));
            throw new AccessDeniedException("No permission to subscribe this chat list.");
        }

        stompOpsMonitor.recordSubscribeSuccess();
    }

    private void handleUnsubscribe(StompHeaderAccessor accessor) {
        chatService.unregisterSessionRoomSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        log.debug("STOMP DISCONNECT: sessionId={}", accessor.getSessionId());
        sessionRegistry.remove(accessor.getSessionId());
        chatService.removeSessionInfo(accessor.getSessionId());
    }

    private void handleSend(StompHeaderAccessor accessor) {
        Long userId = extractUserId(accessor);
        if (userId == null) {
            throw new AccessDeniedException("Unable to resolve authenticated user.");
        }
        ensureActiveUser(userId);
        ensureAuthorizedSession(accessor, userId);

        String destination = accessor.getDestination();
        if (!ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            throw new AccessDeniedException("Unsupported STOMP send destination.");
        }
    }

    private void ensureAuthorizedSession(StompHeaderAccessor accessor, Long userId) {
        if (!sessionRegistry.isAuthorized(accessor.getSessionId(), userId)) {
            throw new AccessDeniedException("STOMP session is no longer authorized.");
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(bearerToken)) {
            bearerToken = accessor.getFirstNativeHeader("authorization");
        }

        if (!StringUtils.hasText(bearerToken)) {
            throw new IllegalArgumentException("Authorization 헤더는 필수입니다.");
        }

        if (!bearerToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 헤더는 Bearer 토큰 형식이어야 합니다.");
        }

        String token = bearerToken.substring(7);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("JWT 토큰이 비어 있습니다.");
        }

        return token;
    }

    private String resolveAuthHeaderKey(StompHeaderAccessor accessor) {
        if (StringUtils.hasText(accessor.getFirstNativeHeader("Authorization"))) {
            return "Authorization";
        }
        if (StringUtils.hasText(accessor.getFirstNativeHeader("authorization"))) {
            return "authorization";
        }
        return "missing";
    }

    private String extractSubjectForLog(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return "unknown";
            }

            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String marker = "\"sub\":\"";
            int start = payload.indexOf(marker);
            if (start < 0) {
                return "unknown";
            }

            int valueStart = start + marker.length();
            int valueEnd = payload.indexOf('"', valueStart);
            if (valueEnd < 0) {
                return "unknown";
            }

            return payload.substring(valueStart, valueEnd);
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private Long extractUserId(StompHeaderAccessor accessor) {
        Long userIdFromPrincipal = extractUserId(accessor.getUser());
        if (userIdFromPrincipal != null) {
            return userIdFromPrincipal;
        }

        return chatService.getUserIdBySession(accessor.getSessionId());
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof Authentication authentication) {
            Object principalObj = authentication.getPrincipal();
            if (principalObj instanceof CustomUserPrincipal customUserPrincipal) {
                return customUserPrincipal.getUserId();
            }
        }

        if (principal instanceof CustomUserPrincipal customUserPrincipal) {
            return customUserPrincipal.getUserId();
        }

        return null;
    }

    private User ensureActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));

        if (user.getStatus() == UserStatus.DELETED
                || user.getStatus() == UserStatus.SUSPENDED
                || user.getStatus() == UserStatus.RESTRICTED) {
            throw new AccessDeniedException("Blocked user cannot use websocket.");
        }
        return user;
    }
}
