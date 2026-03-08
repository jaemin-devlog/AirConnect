package univ.airconnect.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.service.UserService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(
            @CurrentUserId Long userId,
            @RequestBody SignUpRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("회원가입 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SignUpResponse response = userService.signUp(userId, request);
        log.info("회원가입 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(userId), traceId));
    }
}