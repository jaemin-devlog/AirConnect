package univ.airconnect.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.stomp.StompOpsMonitor;
import univ.airconnect.global.security.stomp.StompOpsSnapshot;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/chat/ops")
@RequiredArgsConstructor
public class ChatOpsController {

    private final StompOpsMonitor stompOpsMonitor;

    @GetMapping("/stomp")
    public ResponseEntity<ApiResponse<StompOpsSnapshot>> getStompOpsSnapshot(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(stompOpsMonitor.snapshot(), traceId));
    }
}

