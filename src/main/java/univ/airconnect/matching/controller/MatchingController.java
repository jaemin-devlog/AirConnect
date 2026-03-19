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

    // ========== 테스트용 엔드포인트 ==========
    @PostMapping("/test/send-request/{fromUserId}/{toUserId}")
    public ResponseEntity<ApiResponse<MatchingConnectResponse>> testSendRequest(
            @PathVariable @Positive Long fromUserId,
            @PathVariable @Positive Long toUserId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("🧪 테스트 매칭 요청 시작: from={}, to={}", fromUserId, toUserId);
        MatchingConnectResponse response = matchingService.connect(fromUserId, toUserId);
        log.info("🧪 테스트 매칭 요청 완료: from={}, to={}", fromUserId, toUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/test/requests/{userId}")
    public ResponseEntity<ApiResponse<MatchingRequestsResponse>> testGetRequests(
            @PathVariable @Positive Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("🧪 테스트 요청 목록 조회: userId={}", userId);
        MatchingRequestsResponse response = matchingService.getRequests(userId);
        log.info("🧪 테스트 요청 목록 조회 완료: userId={}, sent={}, received={}", 
                userId, response.getSentCount(), response.getReceivedCount());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/test/accept/{userId}/{connectionId}")
    public ResponseEntity<ApiResponse<MatchingResponseResponse>> testAcceptRequest(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long connectionId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("🧪 테스트 요청 수락 시작: userId={}, connectionId={}", userId, connectionId);
        MatchingResponseResponse response = matchingService.acceptRequest(userId, connectionId);
        log.info("🧪 테스트 요청 수락 완료: userId={}, connectionId={}", userId, connectionId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/test/reject/{userId}/{connectionId}")
    public ResponseEntity<ApiResponse<MatchingResponseResponse>> testRejectRequest(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long connectionId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("🧪 테스트 요청 거절 시작: userId={}, connectionId={}", userId, connectionId);
        MatchingResponseResponse response = matchingService.rejectRequest(userId, connectionId);
        log.info("🧪 테스트 요청 거절 완료: userId={}, connectionId={}", userId, connectionId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/test/expose/{userId}/{targetUserId}")
    public ResponseEntity<ApiResponse<?>> testExposeCandidate(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long targetUserId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("🧪 테스트 후보자 노출 추가: userId={}, targetUserId={}", userId, targetUserId);
        matchingService.testExposeCandidate(userId, targetUserId);
        log.info("🧪 테스트 후보자 노출 추가 완료: userId={}, targetUserId={}", userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("노출 추가 완료", traceId));
    }

}
