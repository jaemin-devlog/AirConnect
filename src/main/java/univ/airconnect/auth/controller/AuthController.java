package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import univ.airconnect.auth.dto.request.LogoutRequest;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TestTokenRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.service.AuthService;
import univ.airconnect.global.security.resolver.CurrentUserId;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostMapping("/social/login")
    public ResponseEntity<LoginResponse> socialLogin(@RequestBody SocialLoginRequest request) {
        log.info("📲 소셜 로그인 요청: {}", request.getProvider());
        LoginResponse response = authService.socialLogin(request);
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

    /**
     * 개발/테스트 환경에서만 사용 가능한 임시 토큰 생성 엔드포인트
     * 프로덕션 환경에서는 이 엔드포인트를 사용할 수 없습니다.
     */
    @PostMapping("/test/token")
    public ResponseEntity<LoginResponse> createTestToken(@RequestBody TestTokenRequest request) {
        // 개발 환경에서만 허용
        if (!isDevEnvironment()) {
            log.error("❌ 테스트 토큰 생성 실패: 프로덕션 환경에서는 사용 불가");
            return ResponseEntity.status(403).build();
        }

        log.warn("🧪 테스트 토큰 생성 요청: deviceId={}", request.getDeviceId());
        LoginResponse response = authService.createTestToken(request.getDeviceId());
        log.warn("✅ 테스트 토큰 생성 완료");
        return ResponseEntity.ok(response);
    }

    private boolean isDevEnvironment() {
        return activeProfile.contains("dev") || activeProfile.contains("local") || activeProfile.equals("default");
    }
}