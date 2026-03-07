# 🎯 Apple 로그인 통합 변경 사항 요약

## 🔐 Apple 엑세스 토큰 + 리프레시 토큰 받은 후 진행 단계

### ✅ 이미 완료된 작업

```
Apple SDK (iOS/Android)
    ↓
identityToken + authorizationCode 획득
    ↓
Backend로 전송
    ↓
1️⃣ JWT 검증 ✅
   - AppleJwtVerifier.verify()로 identityToken 검증
   - JWT 서명 확인
   - issuer/audience 검증

2️⃣ 사용자 ID 추출 ✅
   - JWT Claims에서 sub (appleUserId) 추출
   
3️⃣ 이메일 추출 ✅
   - JWT Claims에서 email 추출
   - 첫 로그인 시만 제공됨

4️⃣ 사용자 조회/생성 ✅
   - DB에서 Apple ID + email로 사용자 조회
   - 없으면 신규 생성

5️⃣ 내부 JWT 발급 ✅
   - accessToken (15분 유효)
   - refreshToken (30일 유효, Redis 저장)

6️⃣ 클라이언트에 반환 ✅
   - 두 토큰 모두 반환
```

---

## 🚀 Apple 로그인 다음 단계

### Step 1: 토큰 갱신 (선택)
```
accessToken 만료 → refreshToken으로 새로운 accessToken 발급
POST /api/v1/auth/refresh
{
  "refreshToken": "{refreshToken}",
  "deviceId": "{deviceId}"
}
```

### Step 2: API 호출
```
Authorization: Bearer {accessToken}로 모든 API 호출
```

### Step 3: 로그아웃
```
POST /api/v1/auth/logout
{
  "deviceId": "{deviceId}"
}
```

---

## 🔄 Kakao 로그인 코드 재사용 가능성

### ✅ 100% 재사용 가능

#### 1. **AuthService.socialLogin()**
```java
// 기존: Kakao만 처리
// 신규: Apple + Kakao 모두 처리 ✅

SocialAuthClient client = socialAuthResolver.getClient(request.provider());
String socialId = client.getSocialId(request.socialToken());

// Apple 특별 처리 (이메일)
if (request.provider() == SocialProvider.APPLE) {
    email = appleAuthClient.getEmail(request.socialToken());
}

User user = userRepository.findByProviderAndSocialId(...)
            .orElseGet(() -> userRepository.save(User.create(...)));
```

#### 2. **AuthService.refresh()**
```java
// Kakao, Apple 모두 동일하게 사용 가능 ✅
// 내부 refreshToken 기반이므로 소셜 플랫폼 상관없음
```

#### 3. **AuthService.logout()**
```java
// Kakao, Apple 모두 동일하게 사용 가능 ✅
// 내부 refreshToken 제거만 수행
```

#### 4. **RefreshTokenRepository**
```java
// Redis 기반, 모든 소셜 플랫폼 공통 사용 ✅
```

#### 5. **JwtProvider**
```java
// accessToken/refreshToken 발급, 모든 플랫폼 공통 ✅
```

### ⚠️ 조정이 필요한 부분

#### 1. **SocialAuthClient 인터페이스**
```java
// Before: Kakao만 구현
KakaoAuthClient implements SocialAuthClient

// After: Apple도 구현 ✅
AppleAuthClient implements SocialAuthClient
KakaoAuthClient implements SocialAuthClient
```

#### 2. **토큰 검증 방식**
```java
// Kakao: HTTP API 호출
KakaoApiClient.getKakaoUserId(accessToken)
→ POST https://kapi.kakao.com/v2/user/me

// Apple: 로컬 JWT 검증
AppleJwtVerifier.verify(identityToken)
→ 로컬 파싱, 외부 호출 없음 ⭐
```

#### 3. **사용자 정보 수집**
```java
// Kakao: 별도 처리 (현재는 미구현)
email = kakaoApiClient.getEmail(accessToken);

// Apple: identityToken에 포함
email = appleAuthClient.getEmail(identityToken);
```

---

## 📊 상태 비교표

| 기능 | Kakao | Apple | 통합 |
|------|-------|-------|------|
| **소셜 토큰 검증** | ✅ | ✅ | ✅ SocialAuthResolver |
| **사용자 ID 추출** | ✅ | ✅ | ✅ SocialAuthClient |
| **사용자 생성/조회** | ✅ | ✅ | ✅ AuthService |
| **JWT 발급** | ✅ | ✅ | ✅ AuthService |
| **Token 갱신** | ✅ | ✅ | ✅ AuthService |
| **로그아웃** | ✅ | ✅ | ✅ AuthService |
| **Redis 저장** | ✅ | ✅ | ✅ RefreshToken |

---

## 🎨 새로운 아키텍처

```
┌─────────────────────────────────────────┐
│         Client App (iOS/Android)        │
└────────────────┬────────────────────────┘
                 │
        ┌────────▼────────┐
        │  AuthController │
        └────────┬────────┘
                 │
         ┌───────▼────────┐
         │  AuthService   │◄─────────────────┐
         └───────┬────────┘                  │
                 │                    (Apple만 필요)
      ┌──────────▼──────────┐
      │ SocialAuthResolver  │
      └──────────┬──────────┘
                 │
        ┌────────┴────────┐
        │                 │
   ┌────▼────┐      ┌────▼─────┐
   │ Apple   │      │  Kakao   │
   │ Client  │      │  Client  │
   └────┬────┘      └────┬─────┘
        │                │
   ┌────▼───────┐   ┌───▼──────┐
   │   Apple    │   │  Kakao   │
   │  JWT       │   │   API    │
   │ Verifier   │   │  Client  │
   └────────────┘   └──────────┘
        │                │
        └────────┬───────┘
                 │
         ┌───────▼────────┐
         │  User Service  │
         │  + DB Access   │
         └────────────────┘
```

---

## 💻 클라이언트 사용 코드

### Before (구)
```javascript
// Apple 로그인
const response = await fetch('/api/v1/auth/apple/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    identityToken: token,
    authorizationCode: code
  })
});

// Kakao 로그인
const response = await fetch('/api/v1/auth/social/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'KAKAO',
    socialToken: kakaoToken,
    deviceId: uuid
  })
});
```

### After (신) ✅
```javascript
// Apple 로그인 (통합)
const response = await fetch('/api/v1/auth/social/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'APPLE',
    socialToken: identityToken,
    deviceId: uuid  // ⭐ 반드시 필요
  })
});

// Kakao 로그인 (동일 엔드포인트)
const response = await fetch('/api/v1/auth/social/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    provider: 'KAKAO',
    socialToken: kakaoAccessToken,
    deviceId: uuid  // ⭐ 반드시 필요
  })
});

// 응답 (동일)
const { accessToken, refreshToken } = await response.json();
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);
```

---

## 🔑 주요 변경 파일

### 신규
- ✅ `auth/service/oauth/apple/AppleAuthClient.java`

### 수정됨
- ✅ `auth/service/AuthService.java` - Apple 이메일 처리
- ✅ `auth/apple/AppleJwtVerifier.java` - 에러 처리 개선
- ✅ `auth/controller/AppleAuthController.java` - Deprecated 처리
- ✅ `auth/exception/AuthErrorCode.java` - INVALID_APPLE_TOKEN 추가
- ✅ `auth/dto/request/SocialLoginRequest.java` - 주석 업데이트

### Deprecated
- ⚠️ `auth/service/AppleAuthService.java` - 더 이상 사용 안 함

---

## ✨ 최종 체크리스트

### Backend
- [x] AppleAuthClient 생성
- [x] AuthService 수정
- [x] AppleJwtVerifier 개선
- [x] AppleAuthController 호환성 유지
- [x] 에러 코드 확장
- [x] 컴파일 성공
- [x] 빌드 성공

### Frontend (준비 중)
- [ ] `/api/v1/auth/social/login` 사용으로 변경
- [ ] provider: "APPLE" 추가
- [ ] deviceId 생성 및 저장 (UUID)
- [ ] 테스트

### QA
- [ ] Apple 로그인 테스트
- [ ] Kakao 로그인 테스트 (회귀)
- [ ] 토큰 갱신 테스트
- [ ] 로그아웃 테스트
- [ ] 멀티 디바이스 테스트

---

## 🚀 배포 체크리스트

- [ ] Backend 배포
- [ ] Frontend 배포
- [ ] 클라이언트 업데이트 공지
- [ ] 기존 Apple 로그인 사용자 모니터링
- [ ] 성능 모니터링

---

## 📞 Support

**문제 발생 시:**
1. 에러 로그 확인
2. `MIGRATION_COMPLETE.md` 참고
3. `SOCIAL_LOGIN_GUIDE.md` 참고

**연락:** [개발팀]

---

**작업 완료:** 2026년 3월 7일  
**빌드 상태:** ✅ SUCCESS  
**배포 준비:** 준비 완료

