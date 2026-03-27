package univ.airconnect.notification.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.dto.request.PushDevicePermissionUpdateRequest;
import univ.airconnect.notification.dto.request.PushDeviceRegisterRequest;
import univ.airconnect.notification.dto.response.PushDeviceListResponse;
import univ.airconnect.notification.dto.response.PushDeviceResponse;
import univ.airconnect.notification.service.PushDeviceService;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * 모바일 클라이언트용 운영 푸시 디바이스 등록 API를 제공한다.
 */
@Validated
@RestController
@RequestMapping({"/api/v1/push/devices", "/v1/push/devices"})
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @GetMapping
    public ResponseEntity<ApiResponse<PushDeviceListResponse>> getDevices(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        List<PushDeviceResponse> items = pushDeviceService.findActiveDevices(userId).stream()
                .map(PushDeviceResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new PushDeviceListResponse(items.size(), items), traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PushDeviceResponse>> registerDevice(
            @CurrentUserId Long userId,
            @Valid @RequestBody PushDeviceRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);

        PushDevice saved = pushDeviceService.registerOrUpdate(new PushDeviceService.UpsertCommand(
                userId,
                request.getDeviceId(),
                request.getPlatform(),
                request.getProvider() != null ? request.getProvider() : PushProvider.FCM,
                request.getPushToken(),
                request.getApnsToken(),
                request.getNotificationPermissionGranted(),
                request.getAppVersion(),
                request.getOsVersion(),
                request.getLocale(),
                request.getTimezone(),
                request.getLastSeenAt()
        ));

        return ResponseEntity.ok(ApiResponse.ok(PushDeviceResponse.from(saved), traceId));
    }

    @PatchMapping({"/{deviceId}", "/{deviceId}/permission"})
    public ResponseEntity<ApiResponse<PushDeviceResponse>> updatePermission(
            @CurrentUserId Long userId,
            @PathVariable String deviceId,
            @Valid @RequestBody PushDevicePermissionUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        PushDevice pushDevice = pushDeviceService.updatePermission(
                userId,
                deviceId,
                request.getNotificationPermissionGranted(),
                request.getLastSeenAt()
        );
        return ResponseEntity.ok(ApiResponse.ok(PushDeviceResponse.from(pushDevice), traceId));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(
            @CurrentUserId Long userId,
            @PathVariable String deviceId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        pushDeviceService.deactivate(userId, deviceId);
        return ResponseEntity.ok(ApiResponse.ok(traceId));
    }
}
