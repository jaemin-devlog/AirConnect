package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.auth.dto.request.AppleLoginRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.auth.service.AppleAuthService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/apple")
public class AppleAuthController {

    private final AppleAuthService appleAuthService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody AppleLoginRequest request
    ) {

        LoginResponse response = appleAuthService.login(request);

        return ResponseEntity.ok(response);
    }
}