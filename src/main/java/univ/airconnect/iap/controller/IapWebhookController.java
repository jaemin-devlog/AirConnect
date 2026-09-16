package univ.airconnect.iap.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.iap.application.IapWebhookService;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;
import univ.airconnect.iap.google.GooglePubSubPushAuthenticator;

import java.util.Map;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/iap")
@Slf4j
@ConditionalOnProperty(value = "iap.enabled", havingValue = "true")
public class IapWebhookController {

    private final IapWebhookService iapWebhookService;
    private final GooglePubSubPushAuthenticator googlePubSubPushAuthenticator;

    public IapWebhookController(IapWebhookService iapWebhookService,
                                GooglePubSubPushAuthenticator googlePubSubPushAuthenticator) {
        this.iapWebhookService = iapWebhookService;
        this.googlePubSubPushAuthenticator = googlePubSubPushAuthenticator;
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
        return response.isAccepted()
                ? ResponseEntity.ok(ApiResponse.ok(response, traceId))
                : ResponseEntity.badRequest().body(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/notifications")
    public ResponseEntity<ApiResponse<IapWebhookAckResponse>> ingestGoogle(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (!googlePubSubPushAuthenticator.isAuthorized(authorization)) {
            log.warn("IAP Google webhook rejected before payload processing. traceId={}", traceId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("IAP Google webhook received. traceId={}, payloadKeys={}", traceId, payload.keySet());
        IapWebhookAckResponse response = iapWebhookService.ingestGoogleNotification(payload);
        log.info("IAP Google webhook processed. traceId={}, accepted={}", traceId, response.isAccepted());
        return response.isAccepted()
                ? ResponseEntity.ok(ApiResponse.ok(response, traceId))
                : ResponseEntity.badRequest().body(ApiResponse.ok(response, traceId));
    }
}

