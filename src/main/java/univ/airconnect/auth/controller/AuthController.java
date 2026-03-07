package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import univ.airconnect.auth.dto.request.LogoutRequest;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.service.AuthService;
import univ.airconnect.global.security.resolver.CurrentUserId;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/social/login")
    public ResponseEntity<TokenPairResponse> socialLogin(@RequestBody SocialLoginRequest request) {
        log.info("📲 소셜 로그인 요청: {}", request.getProvider());
        TokenPairResponse response = authService.socialLogin(request);
        log.info("✅ 로그인 성공");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@RequestBody TokenRefreshRequest request) {
        log.info("🔄 토큰 갱신 요청");
        TokenPairResponse response = authService.refresh(request);
        log.info("✅ 토큰 갱신 성공");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUserId Long userId,
                                       @RequestBody LogoutRequest request) {
        log.info("🚪 로그아웃 요청: userId={}", userId);
        authService.logout(userId, request.getDeviceId());
        log.info("✅ 로그아웃 성공");
        return ResponseEntity.noContent().build();
    }
}