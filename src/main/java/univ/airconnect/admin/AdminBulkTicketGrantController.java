package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin/tickets/bulk-grants")
@RequiredArgsConstructor
public class AdminBulkTicketGrantController {
    private final AdminBulkTicketGrantService service;

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<AdminDtos.BulkTicketGrantPreview>> preview(
            @CurrentUserId Long adminId, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.preview(adminId), traceId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminDtos.BulkTicketGrantResult>> grant(
            @CurrentUserId Long adminId,
            @Valid @RequestBody AdminRequests.BulkTicketGrantRequest body,
            HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.grant(adminId, body), traceId));
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<ApiResponse<AdminDtos.BulkTicketGrantResult>> get(
            @CurrentUserId Long adminId, @PathVariable String operationId, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.get(adminId, operationId), traceId));
    }
}
