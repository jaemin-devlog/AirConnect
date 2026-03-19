package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
        log.info("Social login request: provider={}", request.getProvider());
        LoginResponse response = authService.socialLogin(request);
        log.info("Social login succeeded");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@RequestBody TokenRefreshRequest request) {
        log.info("Token refresh request");
        TokenPairResponse response = authService.refresh(request);
        log.info("Token refresh succeeded");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CurrentUserId Long userId,
            @RequestBody LogoutRequest request
    ) {
        log.info("Logout request: userId={}", userId);
        authService.logout(userId, request.getDeviceId());
        log.info("Logout succeeded");
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/test/token", "/test-token"})
    public ResponseEntity<LoginResponse> createTestToken(@RequestBody TestTokenRequest request) {
        if (!isDevEnvironment()) {
            log.error("Test token is only available outside production");
            return ResponseEntity.status(403).build();
        }

        log.warn("Test token request: deviceId={}", request.getDeviceId());
        LoginResponse response = authService.createTestToken(request.getDeviceId());
        log.warn("Test token created");
        return ResponseEntity.ok(response);
    }

    private boolean isDevEnvironment() {
        return activeProfile.contains("dev")
                || activeProfile.contains("local")
                || activeProfile.contains("test")
                || activeProfile.equals("default");
    }
}
