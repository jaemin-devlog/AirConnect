package univ.airconnect.iap.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.iap.application.IapWebhookService;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;

import java.util.Map;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/iap")
@Slf4j
public class IapWebhookController {

    private final IapWebhookService iapWebhookService;

    public IapWebhookController(IapWebhookService iapWebhookService) {
        this.iapWebhookService = iapWebhookService;
    }

    @PostMapping("/ios/notifications")
    public ResponseEntity<ApiResponse<IapWebhookAckResponse>> ingestApple(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP Apple webhook received. traceId={}, payloadKeys={}", traceId, payload.keySet());
        IapWebhookAckResponse response = iapWebhookService.ingestAppleNotification(payload);
        log.info("IAP Apple webhook processed. traceId={}, accepted={}", traceId, response.isAccepted());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/notifications")
    public ResponseEntity<ApiResponse<IapWebhookAckResponse>> ingestGoogle(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP Google webhook received. traceId={}, payloadKeys={}", traceId, payload.keySet());
        IapWebhookAckResponse response = iapWebhookService.ingestGoogleNotification(payload);
        log.info("IAP Google webhook processed. traceId={}, accepted={}", traceId, response.isAccepted());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}

