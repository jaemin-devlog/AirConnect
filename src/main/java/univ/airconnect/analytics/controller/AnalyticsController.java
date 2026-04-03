package univ.airconnect.analytics.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.analytics.dto.request.AnalyticsTrackRequest;
import univ.airconnect.analytics.dto.response.AnalyticsTrackResponse;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<AnalyticsTrackResponse>> trackEvents(
            @CurrentUserId Long userId,
            @Valid @RequestBody AnalyticsTrackRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String traceId = (String) httpServletRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        AnalyticsTrackResponse response = analyticsService.trackClientEvents(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
