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
import univ.airconnect.admin.AdminAuditAction;
import univ.airconnect.admin.AdminAuditLogService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.maintenance.dto.request.MaintenanceUpdateRequest;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.util.LinkedHashMap;
import java.util.Map;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin/maintenance")
@RequiredArgsConstructor
public class MaintenanceAdminController {

    private final MaintenanceService maintenanceService;
    private final AdminAuditLogService adminAuditLogService;

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
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("enabled", body.enabled());
        metadata.put("title", body.title());
        metadata.put("message", body.message());
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.MAINTENANCE_UPDATED,
                "MAINTENANCE",
                "global",
                body.enabled() ? "점검 모드를 활성화했습니다." : "점검 모드를 비활성화했습니다.",
                body.message(),
                metadata
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
