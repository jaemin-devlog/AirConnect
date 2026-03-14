package univ.airconnect.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import univ.airconnect.chat.dto.request.ChatMessageRequest;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.security.principal.CustomUserPrincipal;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * websocket "/pub/chat/message"로 들어오는 메시징을 처리한다.
     * StompHandler에서 1차 검증된 후, 여기서 userId를 통해 비즈니스 로직을 수행한다.
     */
    @MessageMapping("/chat/message")
    public void message(
            ChatMessageRequest request,
            Principal principal
    ) {
        Long userId = extractUserId(principal);
        log.info("STOMP SEND: roomId={}, senderId={}", request.getRoomId(), userId);
        
        if (userId == null) {
            log.error("STOMP SEND REJECTED: User not authenticated or principal type mismatch");
            return;
        }

        chatService.sendMessage(userId, request);
    }

    private Long extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            Object principalObj = ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            if (principalObj instanceof CustomUserPrincipal) {
                return ((CustomUserPrincipal) principalObj).getUserId();
            }
        }
        return null;
    }
}
