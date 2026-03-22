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

import java.security.Principal;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private static final String CHAT_ROOM_SUB_PREFIX = "/sub/chat/room/";
    private static final String MATCHING_TEAM_ROOM_SUB_PREFIX = "/sub/matching/team-room/";

    private final JwtProvider jwtProvider;
    private final ChatService chatService;
    private final GMatchingService matchingService;

    public StompHandler(
            JwtProvider jwtProvider,
            @Lazy ChatService chatService,
            @Lazy GMatchingService matchingService
    ) {
        this.jwtProvider = jwtProvider;
        this.chatService = chatService;
        this.matchingService = matchingService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnectWithLogging(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            handleDisconnect(accessor);
        }

        return message;
    }

    private void handleConnectWithLogging(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);
        String tokenSubject = extractSubjectForLog(token);

        log.info("STOMP CONNECT INBOUND: sessionId={}, subject={}, headerKey={}",
                accessor.getSessionId(), tokenSubject, resolveAuthHeaderKey(accessor));

        try {
            jwtProvider.validateAccessToken(token);
            Long userId = jwtProvider.getUserId(token);

            CustomUserPrincipal principal = new CustomUserPrincipal(userId);
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            accessor.setUser(authentication);
            chatService.saveSessionInfo(accessor.getSessionId(), userId);

            log.info("STOMP CONNECT SUCCESS: sessionId={}, subject={}, userId={}",
                    accessor.getSessionId(), tokenSubject, userId);
        } catch (Exception e) {
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
            throw new AccessDeniedException("Unable to resolve authenticated user.");
        }

        if (destination.startsWith(CHAT_ROOM_SUB_PREFIX)) {
            handleChatSubscribe(accessor, destination, userId);
            return;
        }

        if (destination.startsWith(MATCHING_TEAM_ROOM_SUB_PREFIX)) {
            handleMatchingSubscribe(accessor, destination, userId);
        }
    }

    private void handleChatSubscribe(StompHeaderAccessor accessor, String destination, Long userId) {
        Long roomId = extractId(destination, CHAT_ROOM_SUB_PREFIX, "chat room");

        if (!chatService.isMember(roomId, userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: roomId={}, userId={}, reason=not_member", roomId, userId);
            throw new AccessDeniedException("No permission to subscribe this chat room.");
        }

        chatService.enterChatRoom(roomId.toString());
        chatService.mapSessionToRoom(accessor.getSessionId(), roomId.toString());
        chatService.updateLastRead(roomId, userId);

        log.info("STOMP SUBSCRIBE CHAT: sessionId={}, userId={}, roomId={}",
                accessor.getSessionId(), userId, roomId);
    }

    private void handleMatchingSubscribe(StompHeaderAccessor accessor, String destination, Long userId) {
        Long teamRoomId = extractId(destination, MATCHING_TEAM_ROOM_SUB_PREFIX, "team room");
        if (!matchingService.canSubscribeTeamRoom(teamRoomId, userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: teamRoomId={}, userId={}, reason=no_access", teamRoomId, userId);
            throw new AccessDeniedException("No permission to subscribe this matching room.");
        }

        log.info("STOMP SUBSCRIBE MATCHING: sessionId={}, userId={}, teamRoomId={}",
                accessor.getSessionId(), userId, teamRoomId);
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        log.info("STOMP DISCONNECT: sessionId={}", accessor.getSessionId());
        chatService.removeSessionInfo(accessor.getSessionId());
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(bearerToken)) {
            bearerToken = accessor.getFirstNativeHeader("authorization");
        }

        if (!StringUtils.hasText(bearerToken)) {
            throw new IllegalArgumentException("Authorization header is required.");
        }

        if (!bearerToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header must be Bearer token.");
        }

        String token = bearerToken.substring(7);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("JWT token is empty.");
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

    private Long extractId(String destination, String prefix, String target) {
        try {
            Long id = Long.valueOf(destination.substring(prefix.length()));
            if (id <= 0) {
                throw new IllegalArgumentException(target + " id must be positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid destination for " + target);
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
}
