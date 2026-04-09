package univ.airconnect.compatibility.controller;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.compatibility.dto.response.CompatibilityResponse;
import univ.airconnect.compatibility.service.CompatibilityService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;

@Validated
@RestController
@RequestMapping("/api/v1/compatibility")
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    @GetMapping("/{targetUserId}")
    public ResponseEntity<ApiResponse<CompatibilityResponse>> getCompatibility(
            @CurrentUserId Long userId,
            @PathVariable @Positive Long targetUserId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        CompatibilityResponse response = compatibilityService.getCompatibility(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
