package univ.airconnect.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;

    /**
     * 채팅방 생성
     */
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createRoom(
            @RequestParam String name,
            @RequestParam ChatRoomType type,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatRoomResponse response = chatService.createChatRoom(name, type, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 채팅방 참여
     */
    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<ApiResponse<Void>> joinRoom(
            @PathVariable Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        chatService.joinRoom(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    /**
     * 채팅방 나가기
     */
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @PathVariable Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        chatService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    /**
     * 내 채팅방 목록 조회
     */
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> findAllRooms(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        List<ChatRoomResponse> response = chatService.findAllRooms(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 특정 채팅방의 메시지 조회
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> findMessages(
            @PathVariable Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        List<ChatMessageResponse> response = chatService.findMessagesByRoomId(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}