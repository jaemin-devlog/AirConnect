package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import univ.airconnect.auth.dto.request.LogoutRequest;
import univ.airconnect.auth.dto.request.EmailLoginRequest;
import univ.airconnect.auth.dto.request.EmailSignUpRequest;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.service.AuthService;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.global.web.ClientIpResolver;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/social/login")
    public ResponseEntity<LoginResponse> socialLogin(@RequestBody SocialLoginRequest request) {
        log.info("Social login request: provider={}", request.getProvider());
        LoginResponse response = authService.socialLogin(request);
        log.info("Social login succeeded");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/sign-up")
    public ResponseEntity<LoginResponse> emailSignUp(@RequestBody EmailSignUpRequest request) {
        log.info("Email sign-up request");
        LoginResponse response = authService.emailSignUp(request);
        log.info("Email sign-up succeeded");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/login")
    public ResponseEntity<LoginResponse> emailLogin(
            @RequestBody EmailLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Email login request");
        LoginResponse response = authService.emailLogin(request, ClientIpResolver.resolve(httpRequest));
        log.info("Email login succeeded");
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

}
