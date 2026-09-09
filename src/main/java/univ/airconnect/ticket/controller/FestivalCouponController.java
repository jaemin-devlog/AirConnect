package univ.airconnect.ticket.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.ticket.dto.request.FestivalCouponRedeemRequest;
import univ.airconnect.ticket.dto.response.FestivalCouponRedeemResponse;
import univ.airconnect.ticket.service.FestivalCouponService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/tickets/coupons")
@RequiredArgsConstructor
public class FestivalCouponController {

    private final FestivalCouponService festivalCouponService;

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<FestivalCouponRedeemResponse>> redeem(
            @CurrentUserId Long userId,
            @Valid @RequestBody FestivalCouponRedeemRequest body,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        FestivalCouponRedeemResponse response = festivalCouponService.redeem(userId, body.code());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
