package univ.airconnect.verification.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.verification.dto.request.EmailVerificationRequest;
import univ.airconnect.verification.dto.request.EmailVerifyRequest;
import univ.airconnect.verification.service.VerificationService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/verification")
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);

        log.info("Email verification code send requested. email={}", request.getEmail());
        verificationService.sendCode(request.getEmail());
        log.info("Email verification code send completed. email={}", request.getEmail());

        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyCode(
            @CurrentUserId Long userId,
            @Valid @RequestBody EmailVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);

        log.info("Email verification requested. userId={}, email={}", userId, request.getEmail());
        verificationService.verifyCode(userId, request.getEmail(), request.getCode());
        log.info("Email verification completed. userId={}, email={}", userId, request.getEmail());

        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }
}
