package univ.airconnect.moderation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.moderation.dto.request.CreateUserReportRequest;
import univ.airconnect.moderation.dto.response.*;
import univ.airconnect.moderation.service.ModerationSupportService;
import univ.airconnect.moderation.service.UserBlockService;
import univ.airconnect.moderation.service.UserReportService;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Validated
@RestController
@RequestMapping("/api/v1/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final UserReportService userReportService;
    private final UserBlockService userBlockService;
    private final ModerationSupportService moderationSupportService;

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<UserReportResponse>> createReport(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateUserReportRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserReportResponse response = userReportService.createReport(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/reports/me")
    public ResponseEntity<ApiResponse<List<UserReportResponse>>> getMyReports(
            @CurrentUserId Long userId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        List<UserReportResponse> response = userReportService.getMyReports(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/blocks/{blockedUserId}")
    public ResponseEntity<ApiResponse<UserBlockCreateResponse>> block(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long blockedUserId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserBlockCreateResponse response = userBlockService.block(userId, blockedUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    public ResponseEntity<ApiResponse<UserBlockDeleteResponse>> unblock(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long blockedUserId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserBlockDeleteResponse response = userBlockService.unblock(userId, blockedUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/blocks")
    public ResponseEntity<ApiResponse<List<UserBlockStatusResponse>>> getMyBlocks(
            @CurrentUserId Long userId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        List<UserBlockStatusResponse> response = userBlockService.getBlockedUsers(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/blocks/{targetUserId}")
    public ResponseEntity<ApiResponse<UserBlockStatusResponse>> getBlockStatus(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long targetUserId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserBlockStatusResponse response = userBlockService.getBlockStatus(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/support")
    public ResponseEntity<ApiResponse<SupportInfoResponse>> getSupportInfo(HttpServletRequest httpRequest) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SupportInfoResponse response = moderationSupportService.getSupportInfo();
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
