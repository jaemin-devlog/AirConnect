package univ.airconnect.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.chat.dto.request.ChatRoomCreateRequest;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.dto.response.ChatParticipantDetailResponse;
import univ.airconnect.chat.dto.response.ChatParticipantProfileResponse;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@Validated
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
            @RequestBody @Valid ChatRoomCreateRequest createRequest,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatRoomResponse response = chatService.createChatRoom(
                createRequest.getName(),
                createRequest.getType(),
                userId,
                createRequest.getTargetUserId()
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 채팅방 참여
     */
    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<ApiResponse<Void>> joinRoom(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
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
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
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
     * 특정 채팅방의 메시지 조회 (커서 페이징 지원)
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> findMessages(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @Positive(message = "마지막 메시지 ID는 양수여야 합니다.") Long lastMessageId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "조회 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 크기는 100 이하여야 합니다.")
            int size,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        List<ChatMessageResponse> response = chatService.findMessagesByRoomId(roomId, userId, lastMessageId, size);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/rooms/{roomId}/counterpart-profile")
    public ResponseEntity<ApiResponse<ChatParticipantDetailResponse>> getCounterpartProfile(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatParticipantDetailResponse response = chatService.getCounterpartProfile(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/rooms/{roomId}/participants/profiles")
    public ResponseEntity<ApiResponse<List<ChatParticipantDetailResponse>>> getParticipantProfiles(
            @PathVariable @Positive(message = "梨꾪똿諛?ID???묒닔?ъ빞 ?⑸땲??") Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        List<ChatParticipantDetailResponse> response = chatService.getParticipantProfiles(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/rooms/{roomId}/participants/{targetUserId}/profile")
    public ResponseEntity<ApiResponse<ChatParticipantProfileResponse>> getParticipantProfile(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @PathVariable @Positive(message = "대상 사용자 ID는 양수여야 합니다.") Long targetUserId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatParticipantProfileResponse response = chatService.getParticipantProfile(roomId, userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @CurrentUserId Long userId,
            @RequestBody @Valid SendMessageRequest requestBody,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatMessageResponse response = chatService.sendMessage(userId, roomId, requestBody);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> deleteMessage(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @PathVariable @Positive(message = "메시지 ID는 양수여야 합니다.") Long messageId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ChatMessageResponse response = chatService.deleteMessage(userId, roomId, messageId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    /**
     * 특정 채팅방의 읽음 상태 갱신
     */
    @PatchMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> updateReadStatus(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") Long roomId,
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        chatService.updateLastRead(roomId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }
}
