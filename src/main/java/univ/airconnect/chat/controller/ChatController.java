package univ.airconnect.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
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
            @Payload @Valid ChatMessageRequest request,
            Principal principal
    ) {
        Long userId = extractUserId(principal);

        if (userId == null) {
            log.error("STOMP SEND REJECTED: invalid principal. principalType={}",
                    principal != null ? principal.getClass().getName() : "null");
            throw new IllegalStateException("인증된 사용자 정보를 확인할 수 없습니다.");
        }

        log.info("STOMP SEND: roomId={}, senderId={}", request.getRoomId(), userId);
        chatService.sendMessage(userId, request);
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