package univ.airconnect.maintenance.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.maintenance.dto.request.MaintenanceUpdateRequest;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin/maintenance")
@RequiredArgsConstructor
public class MaintenanceAdminController {

    private final MaintenanceService maintenanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> getStatus(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(maintenanceService.getStatus(), traceId));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> updateStatus(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody MaintenanceUpdateRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MaintenanceStatusResponse response = maintenanceService.updateStatus(
                adminUserId,
                body.enabled(),
                body.title(),
                body.message()
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
