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

import java.security.Principal;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private static final String CHAT_ROOM_SUB_PREFIX = "/sub/chat/room/";

    private final JwtProvider jwtProvider;
    private final ChatService chatService;

    public StompHandler(JwtProvider jwtProvider, @Lazy ChatService chatService) {
        this.jwtProvider = jwtProvider;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            handleDisconnect(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        jwtProvider.validateAccessToken(token);
        Long userId = jwtProvider.getUserId(token);

        CustomUserPrincipal principal = new CustomUserPrincipal(userId);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        accessor.setUser(authentication);
        chatService.saveSessionInfo(accessor.getSessionId(), userId);

        log.info("STOMP CONNECT: sessionId={}, userId={}", accessor.getSessionId(), userId);
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();

        if (!StringUtils.hasText(destination) || !destination.startsWith(CHAT_ROOM_SUB_PREFIX)) {
            return;
        }

        Long roomId = extractRoomId(destination);
        Long userId = extractUserId(accessor);

        if (userId == null) {
            log.error("STOMP SUBSCRIBE REJECTED: sessionId={}, destination={}, reason=unauthenticated",
                    accessor.getSessionId(), destination);
            throw new AccessDeniedException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        if (!chatService.isMember(roomId, userId)) {
            log.error("STOMP SUBSCRIBE REJECTED: roomId={}, userId={}, reason=not_member", roomId, userId);
            throw new AccessDeniedException("채팅방 참여 권한이 없습니다.");
        }

        chatService.enterChatRoom(roomId.toString());
        chatService.mapSessionToRoom(accessor.getSessionId(), roomId.toString());

        // 채팅방 입장 시 읽음 처리 업데이트
        chatService.updateLastRead(roomId, userId);

        log.info("STOMP SUBSCRIBE: sessionId={}, userId={}, roomId={}",
                accessor.getSessionId(), userId, roomId);
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        log.info("STOMP DISCONNECT: sessionId={}", accessor.getSessionId());
        chatService.removeSessionInfo(accessor.getSessionId());
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");

        if (!StringUtils.hasText(bearerToken)) {
            throw new IllegalArgumentException("Authorization 헤더가 없습니다.");
        }

        if (!bearerToken.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 헤더 형식이 올바르지 않습니다.");
        }

        String token = bearerToken.substring(7);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("JWT 토큰이 비어 있습니다.");
        }

        return token;
    }

    private Long extractRoomId(String destination) {
        try {
            Long roomId = Long.valueOf(destination.substring(CHAT_ROOM_SUB_PREFIX.length()));
            if (roomId <= 0) {
                throw new IllegalArgumentException("채팅방 ID는 양수여야 합니다.");
            }
            return roomId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 채팅방 경로입니다.");
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