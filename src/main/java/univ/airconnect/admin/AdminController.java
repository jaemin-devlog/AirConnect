package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.moderation.domain.ReportStatus;
import univ.airconnect.user.domain.UserStatus;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminDtos.UserSummary>>> getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getUsers(page, size, status, keyword), traceId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminDtos.UserDetail>> getUserDetail(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getUserDetail(userId), traceId));
    }

    @PatchMapping("/users/{userId}/actions")
    public ResponseEntity<ApiResponse<AdminDtos.UserDetail>> applyUserAction(
            @PathVariable Long userId,
            @Valid @RequestBody AdminRequests.UserActionRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.applyUserAction(userId, body), traceId));
    }

    @GetMapping("/matchings")
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminDtos.MatchingRecord>>> getMatchings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ConnectionStatus status,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getMatchings(page, size, status, userId), traceId));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminDtos.ReportRecord>>> getReports(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long reportedUserId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getReports(page, size, status, reportedUserId), traceId));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ApiResponse<AdminDtos.ReportRecord>> updateReportStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody AdminRequests.ReportStatusUpdateRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.updateReportStatus(reportId, body), traceId));
    }

    @GetMapping("/tickets/users/{userId}")
    public ResponseEntity<ApiResponse<AdminDtos.TicketBalance>> getTicketBalance(
            @PathVariable Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getTicketBalance(userId), traceId));
    }

    @GetMapping("/tickets/users/{userId}/ledger")
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminDtos.TicketLedgerItem>>> getTicketLedger(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getTicketLedger(userId, page, size), traceId));
    }

    @PostMapping("/tickets/adjustments")
    public ResponseEntity<ApiResponse<AdminDtos.TicketBalance>> adjustTickets(
            @Valid @RequestBody AdminRequests.TicketAdjustmentRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.adjustTickets(body), traceId));
    }

    @GetMapping("/statistics/overview")
    public ResponseEntity<ApiResponse<AdminDtos.StatisticsOverview>> getStatisticsOverview(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getStatisticsOverview(), traceId));
    }

    @GetMapping("/notices")
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminDtos.NoticeSummary>>> getNotices(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getNotices(page, size), traceId));
    }

    @GetMapping("/notices/{noticeId}")
    public ResponseEntity<ApiResponse<AdminDtos.NoticeDetail>> getNoticeDetail(
            @PathVariable Long noticeId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.getNoticeDetail(noticeId), traceId));
    }

    @PostMapping("/notices/broadcast")
    public ResponseEntity<ApiResponse<AdminDtos.NoticeBroadcastResult>> broadcastNotice(
            @CurrentUserId Long adminUserId,
            @Valid @RequestBody AdminRequests.NoticeBroadcastRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.broadcastNotice(adminUserId, body), traceId));
    }
}
