# 📋 Apple 로그인 Backend API 명세

## 🎯 사용 가능한 엔드포인트

### 1️⃣ Apple 로그인
```
POST /api/v1/auth/social/login
```

#### 요청 (Request)
```json
{
  "provider": "APPLE",
  "socialToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExIn0...",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**필드 설명:**
- `provider`: "APPLE" (고정값)
- `socialToken`: Apple에서 받은 identityToken (JWT 형식)
- `deviceId`: 기기 고유 ID (UUID 권장, 앱에서 생성해서 고정 저장)

#### 성공 응답 (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImV4cCI6MTY0NjY2NjY2Nn0.xxx",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImRldmljZUlkIjoiNTUwZTg0MDAiLCJleHAiOjE2NDkyNDI2NjZ9.xxx"
}
```

**응답 필드:**
- `accessToken`: API 호출 시 사용 (15분 유효)
- `refreshToken`: Token 갱신 시 사용 (30일 유효)

#### 실패 응답 (400/500)
```json
{
  "code": "AUTH_SOCIAL_TOKEN_REQUIRED",
  "message": "소셜 토큰이 필요합니다."
}
```

**가능한 에러 코드:**
- `AUTH_SOCIAL_PROVIDER_REQUIRED`: provider 누락
- `AUTH_SOCIAL_TOKEN_REQUIRED`: socialToken 누락
- `AUTH_DEVICE_ID_REQUIRED`: deviceId 누락
- `AUTH_INVALID_APPLE_TOKEN`: Apple 토큰 검증 실패
- (기타 서버 에러)

---

### 2️⃣ Token 갱신
```
POST /api/v1/auth/refresh
```

#### 요청
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**필드 설명:**
- `refreshToken`: 로그인 시 받은 refreshToken
- `deviceId`: 로그인 시 사용한 동일한 deviceId

#### 성공 응답 (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 실패 응답
```json
{
  "code": "AUTH_REFRESH_TOKEN_NOT_FOUND",
  "message": "저장된 Refresh Token을 찾을 수 없습니다."
}
```

**가능한 에러 코드:**
- `AUTH_DEVICE_MISMATCH`: deviceId 불일치
- `AUTH_REFRESH_TOKEN_MISMATCH`: 토큰 불일치
- `AUTH_REFRESH_TOKEN_NOT_FOUND`: 토큰 만료
- `AUTH_TOKEN_EXPIRED`: 토큰 만료됨

---

### 3️⃣ 로그아웃
```
POST /api/v1/auth/logout
```

#### 요청 헤더
```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### 요청 Body
```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### 성공 응답 (204 No Content)
```
(응답 본문 없음)
```

#### 실패 응답
```json
{
  "code": "AUTH_USER_NOT_FOUND",
  "message": "사용자를 찾을 수 없습니다."
}
```

---

## 🔑 인증 (Authorization)

### API 호출 시 Token 사용

모든 인증이 필요한 API에 다음 헤더 추가:
```
Authorization: Bearer {accessToken}
```

**예시 (사용자 정보 조회):**
```
GET /api/v1/user/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

---

## ⏰ Token 관리

### AccessToken 수명
- **유효 시간:** 15분
- **만료 후:** Token 갱신 필요
- **확인 방법:** 401 Unauthorized 응답 받으면 갱신

### RefreshToken 수명
- **유효 시간:** 30일
- **저장 위치:** 안전한 저장소 (Keychain/Encrypted Storage)
- **만료 후:** 다시 로그인 필요

### 자동 갱신 플로우
```
1. API 호출 → 401 응답
2. RefreshToken 사용해서 Token 갱신
3. 새로운 AccessToken 받기
4. 원래 요청 재시도
```

---

## 📱 실제 사용 예시

### iOS (Swift)
```swift
// 1️⃣ 로그인
func login(identityToken: String) {
    let body = [
        "provider": "APPLE",
        "socialToken": identityToken,
        "deviceId": getOrCreateDeviceId()
    ]
    
    makeRequest(url: "/api/v1/auth/social/login", method: "POST", body: body) { response in
        if let tokens = response.data as? [String: String] {
            KeychainManager.save(accessToken: tokens["accessToken"]!)
            KeychainManager.save(refreshToken: tokens["refreshToken"]!)
        }
    }
}

// 2️⃣ API 호출
func callAPI(endpoint: String) {
    let accessToken = KeychainManager.retrieve(key: "accessToken") ?? ""
    
    makeRequest(url: endpoint, method: "GET", 
                headers: ["Authorization": "Bearer \(accessToken)"]) { response in
        if response.statusCode == 401 {
            // Token 갱신
            refreshToken()
            // 재시도
            callAPI(endpoint: endpoint)
        }
    }
}

// 3️⃣ Token 갱신
func refreshToken() {
    let refreshToken = KeychainManager.retrieve(key: "refreshToken") ?? ""
    let body = [
        "refreshToken": refreshToken,
        "deviceId": getOrCreateDeviceId()
    ]
    
    makeRequest(url: "/api/v1/auth/refresh", method: "POST", body: body) { response in
        if let tokens = response.data as? [String: String] {
            KeychainManager.save(accessToken: tokens["accessToken"]!)
            KeychainManager.save(refreshToken: tokens["refreshToken"]!)
        }
    }
}

// 4️⃣ 로그아웃
func logout() {
    let accessToken = KeychainManager.retrieve(key: "accessToken") ?? ""
    let body = ["deviceId": getOrCreateDeviceId()]
    
    makeRequest(url: "/api/v1/auth/logout", method: "POST", 
                headers: ["Authorization": "Bearer \(accessToken)"],
                body: body) { response in
        KeychainManager.delete(key: "accessToken")
        KeychainManager.delete(key: "refreshToken")
    }
}
```

---

## 🌐 서버 설정

### 개발 환경
```
Base URL: http://localhost:8080
HTTP 통신 가능
```

### 프로덕션 환경
```
Base URL: https://your-server.com
HTTPS 필수 (Apple 요구사항)
```

---

## ✅ 체크리스트

- [ ] Base URL 설정됨
- [ ] identityToken 획득 로직 구현됨
- [ ] Token 저장 로직 구현됨
- [ ] 자동 Token 갱신 로직 구현됨
- [ ] Authorization 헤더 추가됨
- [ ] 에러 처리 구현됨
- [ ] 로그아웃 로직 구현됨

---

## 🚀 테스트 순서

1. **Postman으로 먼저 테스트** (Backend 확인)
2. **Simulator에서 테스트** (기본)
3. **실제 기기에서 테스트** (최종)

---

**Backend 준비 상태: 🌟🌟🌟🌟🌟 100% 완료!**

이 명세만 따라 구현하면 바로 앱에서 Apple 로그인 가능! 🎉


