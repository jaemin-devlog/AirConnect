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
@RequestMapping("/api/v1/admin/tickets/adjustments")
@RequiredArgsConstructor
public class AdminTicketAdjustmentController {
    private final AdminTicketAdjustmentService service;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminDtos.TicketAdjustmentResult>> adjust(
            @CurrentUserId Long adminId, @Valid @RequestBody AdminRequests.TicketAdjustmentRequest body,
            HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.adjust(adminId, body), traceId));
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<ApiResponse<AdminDtos.TicketAdjustmentResult>> get(
            @CurrentUserId Long adminId, @PathVariable String operationId, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(service.get(adminId, operationId), traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.fail(
                new ErrorBody(code.getCode(), "작업번호와 요청 형식을 확인해 주세요.", 400, traceId, null), traceId));
    }
}
