package univ.airconnect.admin.insights;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/** Inherits /api/v1/admin/** ADMIN authorization and the existing API audit interceptor. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/insights")
public class AdminInsightsController {
    private final AdminInsightsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.overview(from, to), (String) request.getAttribute(TRACE_ID_ATTRIBUTE)));
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> members(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "current") String segment,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String gender,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.members(from, to, segment, department, gender, page),
                (String) request.getAttribute(TRACE_ID_ATTRIBUTE)));
    }
}
