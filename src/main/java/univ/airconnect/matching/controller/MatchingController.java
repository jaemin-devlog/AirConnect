package univ.airconnect.matching.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.matching.dto.response.*;
import univ.airconnect.matching.service.MatchingService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<MatchingStartResponse>> start(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        matchingService.start(userId);
        return ResponseEntity.ok(ApiResponse.ok(new MatchingStartResponse(true), traceId));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<MatchingStartResponse>> stop(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        matchingService.stop(userId);
        return ResponseEntity.ok(ApiResponse.ok(new MatchingStartResponse(false), traceId));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<MatchingRecommendationResponse>> recommend(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MatchingRecommendationResponse response = matchingService.recommend(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/connect/{targetUserId}")
    public ResponseEntity<ApiResponse<MatchingConnectResponse>> connect(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long targetUserId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MatchingConnectResponse response = matchingService.connect(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<MatchingRequestsResponse>> getRequests(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MatchingRequestsResponse response = matchingService.getRequests(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
