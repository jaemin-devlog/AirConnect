# 🔐 통합 소셜 로그인 가이드 (Kakao + Apple)

## 📌 변경 사항 요약

기존에 분리되어 있던 Apple 로그인과 Kakao 로그인을 하나의 통합 소셜 로그인 흐름으로 통합했습니다.

### Before (변경 전)
```
AppleAuthController (/api/v1/auth/apple/login)
  └─ AppleAuthService
      └─ AppleJwtVerifier

AuthController (/api/v1/auth/social/login)
  └─ AuthService
      └─ SocialAuthResolver
          └─ KakaoAuthClient
```

### After (변경 후)
```
AuthController (/api/v1/auth/social/login) ⭐ 통합된 엔드포인트
  └─ AuthService
      └─ SocialAuthResolver
          ├─ AppleAuthClient → AppleJwtVerifier
          └─ KakaoAuthClient → KakaoApiClient
```

---

## 🚀 사용 방법

### 1️⃣ Kakao 로그인

**클라이언트 요청:**
```json
POST /api/v1/auth/social/login
{
  "provider": "KAKAO",
  "socialToken": "{카카오_accessToken}",
  "deviceId": "{클라이언트_고정_ID}"
}
```

**응답:**
```json
{
  "accessToken": "{jwt_accessToken}",
  "refreshToken": "{jwt_refreshToken}"
}
```

### 2️⃣ Apple 로그인

**클라이언트 요청:**
```json
POST /api/v1/auth/social/login
{
  "provider": "APPLE",
  "socialToken": "{apple_identityToken}",
  "deviceId": "{클라이언트_고정_ID}"
}
```

**응답:**
```json
{
  "accessToken": "{jwt_accessToken}",
  "refreshToken": "{jwt_refreshToken}"
}
```

---

## 📂 구조 변경 상세

### 1. AppleAuthClient 생성 ✅
- **위치:** `auth/service/oauth/apple/AppleAuthClient.java`
- **구현:** `SocialAuthClient` 인터페이스
- **역할:** 
  - `getSocialId()`: identityToken 검증 후 appleUserId 추출
  - `getEmail()`: identityToken에서 이메일 추출

### 2. AuthService 개선 ✅
- **추가:** `AppleAuthClient` 주입
- **개선:** `socialLogin()` 메서드에 Apple 이메일 처리 로직 추가

### 3. AppleJwtVerifier 개선 ✅
- **개선:** 에러 처리 강화 (`RuntimeException` → `AuthException`)
- **개선:** 디버깅 로그 제거
- **그대로 사용:** JWT 검증 로직

### 4. AppleAuthService ⚠️
- **상태:** Deprecated (더 이상 사용 안 함)
- **대체:** `AuthService.socialLogin()` 사용

### 5. AppleAuthController ⚠️
- **상태:** Deprecated (더 이상 사용 안 함)
- **대체:** `AuthController`의 `/api/v1/auth/social/login` 사용
- **하위 호환성:** 기존 `/api/v1/auth/apple/login` 엔드포인트는 여전히 작동하지만, 새 엔드포인트 사용 권장

---

## 🔄 로그인 플로우

```
┌─────────────────┐
│  클라이언트 앱   │
│  (iOS/Android)  │
└────────┬────────┘
         │
         ├─ 1️⃣ Kakao SDK / Apple SDK 로그인
         │
         ├─ 2️⃣ socialToken 획득 (accessToken / identityToken)
         │
         ├─ 3️⃣ POST /api/v1/auth/social/login
         │      {
         │        provider: "KAKAO" | "APPLE",
         │        socialToken: token,
         │        deviceId: uuid
         │      }
         │
         └──────────────┬──────────────┐
                        │              │
                  ┌─────▼──────┐   ┌──▼─────────┐
                  │ AuthService│   │SocialAuthClient
                  └─────┬──────┘   │  Resolver
                        │          └──────┬──────┐
                        │                 │      │
                    4️⃣ socialToken ├─┐ AppleAuthClient
                       검증       │ │ KakaoAuthClient
                        │        │ │
                  ┌─────▼──────┐ │ │
                  │ SocialId    │ │ │
                  │ + Email (Apple)
                  └─────┬──────┘ │ │
                        │        └─┘
                        │
                    5️⃣ 사용자 조회 또는 신규가입
                        │
                        │
                  ┌─────▼──────┐
                  │   User     │
                  │  Database  │
                  └─────┬──────┘
                        │
                    6️⃣ JWT 토큰 발급
                        │
                  ┌─────▼──────────────┐
                  │ AccessToken        │
                  │ RefreshToken       │
                  └─────┬──────────────┘
                        │
         ┌──────────────▼──────────────┐
         │  클라이언트에게 반환         │
         │  (Local Storage 저장)       │
         └─────────────────────────────┘
```

---

## 📝 DeviceId란?

`deviceId`는 클라이언트 기기를 고유하게 식별하기 위한 값입니다.

- **생성:** 앱 최초 설치 시 한 번만 생성 (UUID 권장)
- **저장:** 기기 로컬스토리지 또는 secure storage에 저장
- **용도:** 
  - 멀티 디바이스 토큰 관리
  - 토큰 갱신 시 기기 검증
  - 보안 강화

**예시:**
```javascript
// React Native
import { v4 as uuidv4 } from 'uuid';

const deviceId = uuidv4();
// "550e8400-e29b-41d4-a716-446655440000"
```

---

## 🔑 Token 갱신

Refresh Token으로 새로운 Access Token 획득:

```json
POST /api/v1/auth/refresh
{
  "refreshToken": "{refreshToken}",
  "deviceId": "{클라이언트_고정_ID}"
}
```

**응답:**
```json
{
  "accessToken": "{new_jwt_accessToken}",
  "refreshToken": "{new_jwt_refreshToken}"
}
```

---

## 🚪 로그아웃

```json
POST /api/v1/auth/logout
{
  "deviceId": "{클라이언트_고정_ID}"
}
```

*Authorization Header*: `Bearer {accessToken}`

---

## ⚙️ Apple 특이 사항

### identityToken 구조

Apple이 제공하는 identityToken은 JWT 형식이며, 다음 정보를 포함합니다:

```json
{
  "iss": "https://appleid.apple.com",
  "aud": "com.jiin.AirConnect",
  "sub": "{appleUserId}",
  "email": "{userEmail}",
  "iat": 1234567890,
  "exp": 1234571490
}
```

### 이메일 처리

- **첫 로그인:** identityToken에서 이메일 추출, DB에 저장
- **이후 로그인:** 저장된 이메일 사용
- **주의:** Apple은 첫 번째 요청에만 이메일을 제공 (앱 개발자 설정 필요)

---

## 🔍 Kakao 토큰과 Apple 토큰의 차이점

| 항목 | Kakao | Apple |
|------|-------|-------|
| **토큰 형식** | Opaque String | JWT |
| **검증 방식** | API 호출 필요 | 로컬 JWT 파싱 |
| **검증 속도** | 느림 (외부 API) | 빠름 (로컬) |
| **이메일** | API 호출로 별도 획득 | Token에 포함 |
| **공개키** | 고정 | 주기적 갱신 (JWKS) |

---

## 📚 파일 구조

```
auth/
├── apple/
│   ├── AppleJwtVerifier.java         ✅ 개선됨
│   └── ApplePublicKeyProvider.java
├── service/
│   ├── AuthService.java              ✅ 개선됨
│   ├── AppleAuthService.java         ⚠️  Deprecated
│   └── oauth/
│       ├── SocialAuthClient.java
│       ├── SocialAuthResolver.java
│       ├── apple/
│       │   └── AppleAuthClient.java   ✅ 신규 생성
│       └── kakao/
│           ├── KakaoAuthClient.java
│           ├── KakaoApiClient.java
│           ├── KakaoConfig.java
│           └── KakaoProperties.java
├── controller/
│   ├── AuthController.java           ✅ 통합 엔드포인트
│   └── AppleAuthController.java      ⚠️  Deprecated
├── dto/
│   ├── request/
│   │   ├── SocialLoginRequest.java    ✅ 업데이트됨
│   │   ├── AppleLoginRequest.java
│   │   └── ...
│   └── response/
│       └── ...
└── exception/
    └── AuthErrorCode.java            ✅ INVALID_APPLE_TOKEN 추가
```

---

## ✨ 주요 개선 사항

### 1. 코드 통합
- Apple과 Kakao를 동일한 인터페이스로 관리
- 중복 코드 제거
- 유지보수성 향상

### 2. 일관된 API
- 모든 소셜 로그인이 `/api/v1/auth/social/login` 사용
- 클라이언트 코드 단순화

### 3. 에러 처리 개선
- 구체적인 에러 코드 (`INVALID_APPLE_TOKEN`)
- 일관된 예외 처리 (`AuthException`)

### 4. 이메일 저장
- Apple 로그인 시 이메일 자동 저장
- 사용자 정보 관리 용이

---

## 🛠️ 마이그레이션 가이드 (클라이언트)

### Before (Old)
```javascript
// Apple 로그인 (구)
const response = await fetch('/api/v1/auth/apple/login', {
  method: 'POST',
  body: JSON.stringify({
    identityToken: token,
    authorizationCode: code
  })
});
```

### After (New)
```javascript
// Apple 로그인 (신)
const response = await fetch('/api/v1/auth/social/login', {
  method: 'POST',
  body: JSON.stringify({
    provider: 'APPLE',
    socialToken: identityToken,
    deviceId: storedDeviceId
  })
});

// Kakao 로그인 (변경 없음)
const response = await fetch('/api/v1/auth/social/login', {
  method: 'POST',
  body: JSON.stringify({
    provider: 'KAKAO',
    socialToken: kakaoAccessToken,
    deviceId: storedDeviceId
  })
});
```

---

## 📞 문제 해결

### Q: "유효하지 않은 Apple 토큰입니다" 에러가 발생합니다.

**A:**
1. identityToken이 올바르게 전달되었는지 확인
2. Apple 앱 ID (`com.jiin.AirConnect`)와 프로젝트 설정 일치 확인
3. Apple 공개키가 최신인지 확인 (자동 갱신)

### Q: 기존 `/api/v1/auth/apple/login` 엔드포인트를 계속 사용할 수 있나요?

**A:** 하위 호환성을 위해 여전히 작동하지만, 곧 제거될 예정입니다. 새 엔드포인트로 마이그레이션해주세요.

### Q: 이메일이 저장되지 않습니다.

**A:** Apple은 첫 로그인 시에만 이메일을 제공합니다. 이후 로그인부터는 저장된 이메일 사용합니다. 앱 설정에서 이메일 권한이 활성화되었는지 확인하세요.

---

**작성일:** 2026년 3월 7일  
**버전:** 1.0 (통합 소셜 로그인)

