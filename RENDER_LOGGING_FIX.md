# 🔍 Render 배포 후 로그 안 보임 문제 해결

## 📋 문제 원인 분석

### 가능한 원인들

1. **로그 출력 설정 문제**
   - application.yml에서 로그 레벨이 ERROR로 설정됨
   - Spring Boot 기본 로그 레벨 문제

2. **Render 로그 보기 방식 문제**
   - Render 대시보드 → Logs 탭에서 확인해야 함
   - 실시간 로그가 아닐 수 있음

3. **배포된 코드와 로컬 코드 불일치**
   - 로그 코드가 포함되지 않음
   - 소스 코드 재배포 필요

4. **CORS 또는 다른 문제로 요청 실패**
   - 요청이 도달하지 않음
   - 네트워크 문제

---

## ✅ 해결 방법

### Step 1️⃣: 로그 레벨 설정 확인

**파일:** `application.yml` (또는 `application-prod.yml`)

```yaml
# Before (문제)
logging:
  level:
    root: ERROR

# After (해결)
logging:
  level:
    root: INFO
    univ.airconnect: DEBUG  # 당신의 패키지
```

---

### Step 2️⃣: AuthService에 로그 추가

**파일:** `src/main/java/univ/airconnect/auth/service/AuthService.java`

현재 코드에 로그를 추가하겠습니다:

```java
package univ.airconnect.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
// ... 기타 import ...

@Slf4j  // ⭐ 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    
    private final SocialAuthResolver socialAuthResolver;
    private final AppleAuthClient appleAuthClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenPairResponse socialLogin(SocialLoginRequest request) {
        log.info("🔐 소셜 로그인 시작: provider={}", request.getProvider());  // ⭐ 추가
        
        validateSocialLoginRequest(request);

        SocialAuthClient client = socialAuthResolver.getClient(request.getProvider());
        String socialId = client.getSocialId(request.getSocialToken());
        log.debug("✅ Social ID 획득: {}", socialId);  // ⭐ 추가

        // Apple 로그인인 경우 email 정보 추출
        final String email;
        if (request.getProvider() == SocialProvider.APPLE) {
            email = appleAuthClient.getEmail(request.getSocialToken());
            log.debug("✅ Apple 이메일 획득: {}", email);  // ⭐ 추가
        } else {
            email = null;
        }

        User user = userRepository.findByProviderAndSocialId(request.getProvider(), socialId)
                .orElseGet(() -> {
                    log.info("👤 신규 사용자 생성: provider={}, socialId={}", 
                             request.getProvider(), socialId);  // ⭐ 추가
                    return userRepository.save(User.create(request.getProvider(), socialId, email));
                });
        
        log.info("✅ 사용자 조회/생성 완료: userId={}", user.getId());  // ⭐ 추가

        validateUserStatus(user);

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());
        
        log.debug("🎫 토큰 생성 완료: userId={}, deviceId={}", user.getId(), request.getDeviceId());  // ⭐ 추가

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), refreshToken)
        );
        
        log.info("💾 RefreshToken 저장 완료: userId={}", user.getId());  // ⭐ 추가

        return new TokenPairResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenPairResponse refresh(TokenRefreshRequest request) {
        log.info("🔄 토큰 갱신 시작: deviceId={}", request.getDeviceId());  // ⭐ 추가
        
        validateRefreshRequest(request);

        jwtProvider.validateRefreshToken(request.getRefreshToken());

        if (!jwtProvider.isRefreshToken(request.getRefreshToken())) {
            log.warn("⚠️ RefreshToken이 아님");  // ⭐ 추가
            throw new AuthException(AuthErrorCode.NOT_REFRESH_TOKEN);
        }

        Long userId = jwtProvider.getUserId(request.getRefreshToken());
        String deviceIdFromToken = jwtProvider.getDeviceId(request.getRefreshToken());
        
        log.debug("📍 토큰에서 정보 추출: userId={}, deviceId={}", userId, deviceIdFromToken);  // ⭐ 추가

        if (!request.getDeviceId().equals(deviceIdFromToken)) {
            log.warn("⚠️ DeviceId 불일치: 요청={}, 토큰={}", request.getDeviceId(), deviceIdFromToken);  // ⭐ 추가
            throw new AuthException(AuthErrorCode.DEVICE_MISMATCH);
        }

        String refreshTokenKey = buildRefreshTokenKey(userId, request.getDeviceId());

        RefreshToken savedToken = refreshTokenRepository.findById(refreshTokenKey)
                .orElseThrow(() -> {
                    log.error("❌ RefreshToken 찾을 수 없음: key={}", refreshTokenKey);  // ⭐ 추가
                    return new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
                });

        if (!savedToken.getToken().equals(request.getRefreshToken())) {
            log.error("❌ RefreshToken 불일치");  // ⭐ 추가
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ 사용자 찾을 수 없음: userId={}", userId);  // ⭐ 추가
                    return new AuthException(AuthErrorCode.USER_NOT_FOUND);
                });

        validateUserStatus(user);

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), request.getDeviceId());

        refreshTokenRepository.save(
                RefreshToken.create(user.getId(), request.getDeviceId(), newRefreshToken)
        );
        
        log.info("✅ 토큰 갱신 완료: userId={}", userId);  // ⭐ 추가

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(Long userId, String deviceId) {
        log.info("🚪 로그아웃: userId={}, deviceId={}", userId, deviceId);  // ⭐ 추가
        
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            log.warn("⚠️ 로그아웃 요청 유효하지 않음");  // ⭐ 추가
            throw new AuthException(AuthErrorCode.INVALID_LOGOUT_REQUEST);
        }

        refreshTokenRepository.deleteById(buildRefreshTokenKey(userId, deviceId));
        log.info("✅ RefreshToken 삭제 완료: userId={}", userId);  // ⭐ 추가
    }

    // ... 나머지 메서드 ...
}
```

---

### Step 3️⃣: application.yml 설정

**파일:** `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: AirConnect
  
  # ... 기타 설정 ...

# ⭐ 로그 설정 추가
logging:
  level:
    root: INFO
    univ.airconnect: DEBUG
    univ.airconnect.auth: DEBUG  # Auth 패키지만 더 자세히
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/airconnect.log
    max-size: 10MB
    max-history: 30
```

---

### Step 4️⃣: AuthController에도 로그 추가

**파일:** `src/main/java/univ/airconnect/auth/controller/AuthController.java`

```java
package univ.airconnect.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;  // ⭐ 추가
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import univ.airconnect.auth.dto.request.LogoutRequest;
import univ.airconnect.auth.dto.request.SocialLoginRequest;
import univ.airconnect.auth.dto.request.TokenRefreshRequest;
import univ.airconnect.auth.dto.response.TokenPairResponse;
import univ.airconnect.auth.service.AuthService;
import univ.airconnect.global.security.resolver.CurrentUserId;

@Slf4j  // ⭐ 추가
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/social/login")
    public ResponseEntity<TokenPairResponse> socialLogin(@RequestBody SocialLoginRequest request) {
        log.info("📲 소셜 로그인 요청: {}", request.getProvider());  // ⭐ 추가
        TokenPairResponse response = authService.socialLogin(request);
        log.info("✅ 로그인 성공");  // ⭐ 추가
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@RequestBody TokenRefreshRequest request) {
        log.info("🔄 토큰 갱신 요청");  // ⭐ 추가
        TokenPairResponse response = authService.refresh(request);
        log.info("✅ 토큰 갱신 성공");  // ⭐ 추가
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUserId Long userId,
                                       @RequestBody LogoutRequest request) {
        log.info("🚪 로그아웃 요청: userId={}", userId);  // ⭐ 추가
        authService.logout(userId, request.getDeviceId());
        log.info("✅ 로그아웃 성공");  // ⭐ 추가
        return ResponseEntity.noContent().build();
    }
}
```

---

## 🚀 배포 후 로그 확인하기

### Render 대시보드에서

1. Render 대시보드 접속
2. 서비스 선택
3. "Logs" 탭 클릭
4. 실시간 로그 확인

```
📲 소셜 로그인 요청: APPLE
✅ Social ID 획득: 001047.xxx
💾 RefreshToken 저장 완료: userId=1
✅ 로그인 성공
```

---

## 📝 배포 단계

### 1️⃣ 로컬에서 테스트
```bash
./gradlew bootRun
# Postman에서 요청 → 로그 확인
```

### 2️⃣ GitHub에 push
```bash
git add .
git commit -m "Add detailed logging for authentication"
git push origin main
```

### 3️⃣ Render 자동 배포
```
Render가 자동으로 새 코드 빌드 및 배포
```

### 4️⃣ 배포된 서버에서 테스트
```
https://your-app.onrender.com/api/v1/auth/social/login
→ Render Logs 탭에서 로그 확인
```

---

## 🔍 로그 수준 설명

| 수준 | 용도 | 예시 |
|------|------|------|
| **ERROR** | 심각한 오류 | ❌ RefreshToken 찾을 수 없음 |
| **WARN** | 경고 | ⚠️ DeviceId 불일치 |
| **INFO** | 일반 정보 | 📲 로그인 요청 시작 |
| **DEBUG** | 디버그 정보 | 📍 토큰에서 정보 추출 |

---

## 💡 추가 팁

### 프로덕션 vs 개발 로그 분리

**application-prod.yml:**
```yaml
logging:
  level:
    root: WARN
    univ.airconnect: INFO
  # 프로덕션은 덜 자세하게
```

**application.yml:**
```yaml
logging:
  level:
    root: INFO
    univ.airconnect: DEBUG
  # 개발은 더 자세하게
```

---

## ✅ 체크리스트

- [ ] AuthService에 @Slf4j 추가
- [ ] 주요 메서드에 log.info() 추가
- [ ] application.yml 로그 레벨 설정
- [ ] 로컬에서 로그 확인
- [ ] GitHub에 push
- [ ] Render 자동 배포 대기
- [ ] Render Logs 탭에서 확인
- [ ] 프론트에서 다시 요청
- [ ] 로그 출력 확인 ✅

---

**이제 Render 로그가 보일 거예요!** 🎉


