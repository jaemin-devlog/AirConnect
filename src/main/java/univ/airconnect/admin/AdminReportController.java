package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.security.resolver.CurrentUserId;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final AdminReportService service;

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<AdminDtos.ReportDetail>> get(@CurrentUserId Long adminId,
            @PathVariable Long reportId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.get(adminId, reportId), traceId(request)));
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<ApiResponse<AdminDtos.ReportDetail>> update(@CurrentUserId Long adminId,
            @PathVariable Long reportId, @Valid @RequestBody AdminRequests.ReportStatusUpdateRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.update(adminId, reportId, body), traceId(request)));
    }

    @PostMapping("/{reportId}/evidence-history")
    public ResponseEntity<ApiResponse<AdminDtos.ChatHistory>> history(@CurrentUserId Long adminId,
            @PathVariable Long reportId, @Valid @RequestBody AdminRequests.ReportHistoryRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.readEvidenceHistory(adminId, reportId, body, traceId(request)), traceId(request)));
    }

    @PostMapping("/{reportId}/evidence-inspections")
    public ResponseEntity<ApiResponse<AdminDtos.ChatMessageInspection>> inspect(@CurrentUserId Long adminId,
            @PathVariable Long reportId, @Valid @RequestBody AdminRequests.ReportEvidenceInspectionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.inspectEvidence(adminId, reportId, body, traceId(request)), traceId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(HttpServletRequest request) {
        String trace = traceId(request);
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.fail(
                new ErrorBody(ErrorCode.INVALID_REQUEST.getCode(), "신고 요청 형식을 확인해 주세요.", 400, trace, null), trace));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
    }
}
