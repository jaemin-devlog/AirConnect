package univ.airconnect.referral.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.referral.dto.request.ReferralRedeemRequest;
import univ.airconnect.referral.dto.response.ReferralMeResponse;
import univ.airconnect.referral.dto.response.ReferralRedeemResponse;
import univ.airconnect.referral.service.ReferralService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ReferralMeResponse>> getMe(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(referralService.getMe(userId), traceId));
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<ReferralRedeemResponse>> redeem(
            @CurrentUserId Long userId,
            @Valid @RequestBody ReferralRedeemRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(
                referralService.redeem(userId, body.referralCode()),
                traceId
        ));
    }
}
