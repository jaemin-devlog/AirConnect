package univ.airconnect.ads.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.ads.dto.response.AdRewardCallbackResponse;
import univ.airconnect.ads.dto.response.AdRewardSessionCreateResponse;
import univ.airconnect.ads.service.AdRewardCallbackService;
import univ.airconnect.ads.service.AdRewardSessionService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/ads/rewards")
@RequiredArgsConstructor
@Slf4j
public class AdRewardController {

    private final AdRewardSessionService adRewardSessionService;
    private final AdRewardCallbackService adRewardCallbackService;

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<AdRewardSessionCreateResponse>> createSession(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        AdRewardSessionCreateResponse response = adRewardSessionService.createSession(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/callback/admob")
    public ResponseEntity<ApiResponse<AdRewardCallbackResponse>> admobCallback(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        AdRewardCallbackResponse response = adRewardCallbackService.handleAdmobCallback(request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}

