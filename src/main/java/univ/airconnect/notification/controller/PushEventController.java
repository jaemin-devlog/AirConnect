package univ.airconnect.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.notification.domain.entity.PushEvent;
import univ.airconnect.notification.dto.request.PushEventCreateRequest;
import univ.airconnect.notification.dto.response.PushEventResponse;
import univ.airconnect.notification.service.PushEventService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * 클라이언트가 보고한 푸시 수신/열람 이벤트를 저장한다.
 */
@Validated
@RestController
@RequestMapping({"/api/v1/push/events", "/v1/push/events"})
@RequiredArgsConstructor
public class PushEventController {

    private final PushEventService pushEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<PushEventResponse>> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushEventCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        PushEvent pushEvent = pushEventService.create(
                userId,
                new PushEventService.CreateCommand(
                        request.getNotificationId(),
                        request.getProviderMessageId(),
                        request.getEventType(),
                        request.getOccurredAt(),
                        request.getDeviceId()
                )
        );
        return ResponseEntity.ok(ApiResponse.ok(PushEventResponse.from(pushEvent), traceId));
    }
}
