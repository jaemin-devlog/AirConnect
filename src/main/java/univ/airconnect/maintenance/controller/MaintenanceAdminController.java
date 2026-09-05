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
import univ.airconnect.maintenance.dto.request.MaintenanceContentUpdateRequest;
import univ.airconnect.maintenance.dto.request.MaintenanceStateUpdateRequest;
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
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.ok(maintenanceService.getStatus(), traceId));
    }

    @PatchMapping("/content")
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> updateContent(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody MaintenanceContentUpdateRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MaintenanceStatusResponse response = maintenanceService.updateContent(
                adminUserId, body.expectedVersion(), body.title(), body.message());
        recordChange(adminUserId, response, "CONTENT", "점검 안내 문구를 변경했습니다.");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(response, traceId));
    }

    @PatchMapping("/state")
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> changeState(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody MaintenanceStateUpdateRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MaintenanceStatusResponse response = maintenanceService.changeState(
                adminUserId, body.expectedVersion(), body.enabled());
        recordChange(adminUserId, response, "STATE", body.enabled() ? "점검을 시작했습니다." : "점검을 종료했습니다.");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(response, traceId));
    }

    private void recordChange(Long adminUserId, MaintenanceStatusResponse response, String operation, String summary) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operation", operation);
        metadata.put("version", response.version());
        metadata.put("enabled", response.enabled());
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.MAINTENANCE_UPDATED,
                "MAINTENANCE",
                "global",
                summary,
                null,
                metadata
        );
    }
}
