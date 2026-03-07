# 📱 Postman에서 Kakao 로그인 테스트하기

## 🎯 Kakao 로그인 플로우

```
1️⃣ iOS/Android 카카오 SDK 로그인
   ↓
2️⃣ 카카오 accessToken 획득
   ↓
3️⃣ Backend로 요청
   ↓
4️⃣ 내부 JWT 토큰 받기
```

---

## 🚀 Postman에서 테스트하기

### Step 1️⃣: 카카오 accessToken 획득

**방법 A: 카카오 개발자 콘솔에서 테스트 토큰 생성**

1. https://developers.kakao.com 접속
2. 로그인 후 왼쪽 "내 애플리케이션"
3. 애플리케이션 선택
4. "도구" → "REST API 테스트"
5. "/v2/user/me" 엔드포인트 선택
6. Authorization 헤더의 토큰 복사

**방법 B: 실제 카카오 로그인으로 토큰 받기**

```
카카오 SDK (iOS/Android)로 로그인 → 토큰 획득
```

---

### Step 2️⃣: Postman 설정

#### URL 입력
```
POST http://localhost:8080/api/v1/auth/social/login
```

#### Headers 설정
```
Content-Type: application/json
```

#### Body 설정 (Raw JSON)
```json
{
  "provider": "KAKAO",
  "socialToken": "{카카오_accessToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**필드 설명:**
- `provider`: "KAKAO" (고정값)
- `socialToken`: 카카오에서 받은 accessToken
- `deviceId`: UUID (기기 고유 ID)

---

### Step 3️⃣: Send 클릭

#### 성공 응답 (200 OK)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 실패 응답 예시
```json
{
  "code": "AUTH_SOCIAL_TOKEN_REQUIRED",
  "message": "소셜 토큰이 필요합니다."
}
```

---

## 🔑 카카오 accessToken 얻는 방법 (상세)

### 방법 1️⃣: Kakao Developers 콘솔 (가장 쉬움)

1. https://developers.kakao.com/console 접속
2. "내 애플리케이션" 선택
3. 왼쪽 메뉴 "도구" → "REST API 테스트"
4. **엔드포인트:** `/v2/user/me` 선택
5. **GET 요청** 클릭
6. 응답 확인

**응답 형식:**
```json
{
  "id": 123456789,
  "connected_at": "2024-01-01T00:00:00Z",
  "properties": { ... },
  "kakao_account": { ... }
}
```

7. 브라우저 개발자 도구 → Network 탭 확인
8. Authorization 헤더의 토큰 복사

### 방법 2️⃣: cURL로 토큰 테스트

먼저 카카오 로그인으로 토큰 얻은 후:

```bash
# 토큰 검증
curl -H "Authorization: Bearer YOUR_KAKAO_ACCESS_TOKEN" \
  https://kapi.kakao.com/v2/user/me

# 응답이 오면 토큰이 유효함
```

---

## 📋 Postman 설정 예시 (상세)

### 전체 요청
```
URL: POST http://localhost:8080/api/v1/auth/social/login

Headers:
Content-Type: application/json

Body:
{
  "provider": "KAKAO",
  "socialToken": "T0a9R1Fzp6Jk8N2M5L3Q4W7E0R9Y2U5X8C1V4B7N0M3Z6A9D2F5G8H1J4K7L0P3S6T9",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}

Response (200 OK):
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImV4cCI6MTY0NjY2NjY2Nn0.xxx",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImRldmljZUlkIjoiNTUwZTg0MDAiLCJleHAiOjE2NDkyNDI2NjZ9.xxx"
}
```

---

## 🧪 테스트 시나리오

### Scenario 1️⃣: 첫 로그인 (신규 사용자)

**요청:**
```json
{
  "provider": "KAKAO",
  "socialToken": "{카카오_토큰}",
  "deviceId": "device-1"
}
```

**응답:**
```json
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

**DB 상태:**
- User 테이블에 신규 사용자 생성
- provider = KAKAO
- socialId = 카카오 ID

---

### Scenario 2️⃣: 기존 사용자 재로그인

**동일한 카카오 ID로 다시 로그인:**
```json
{
  "provider": "KAKAO",
  "socialToken": "{같은_카카오_토큰}",
  "deviceId": "device-1"
}
```

**결과:**
- 기존 사용자 조회
- 새로운 accessToken/refreshToken 발급

---

### Scenario 3️⃣: 다른 디바이스에서 로그인

**다른 deviceId로 로그인:**
```json
{
  "provider": "KAKAO",
  "socialToken": "{카카오_토큰}",
  "deviceId": "device-2"
}
```

**결과:**
- Redis에 2개의 RefreshToken 저장
- 각 디바이스별 독립적 관리

---

## 🔗 Apple vs Kakao 비교

| 항목 | Apple | Kakao |
|------|-------|-------|
| **토큰 형식** | JWT (identityToken) | Opaque String (accessToken) |
| **검증 방식** | 로컬 JWT 검증 | Kakao API 호출 |
| **Postman 테스트** | 어려움 (실제 앱 필요) | 쉬움 (콘솔에서 토큰 얻기 가능) |
| **엔드포인트** | `/api/v1/auth/social/login` | `/api/v1/auth/social/login` |
| **provider 값** | "APPLE" | "KAKAO" |

---

## ✅ 체크리스트

- [ ] Kakao 애플리케이션 생성됨
- [ ] Kakao accessToken 획득됨
- [ ] Postman에서 요청 설정됨
- [ ] Backend 서버 실행 중 (http://localhost:8080)
- [ ] Redis 서버 실행 중
- [ ] Response 확인됨

---

## 📝 Postman Collection 만들기

프로젝트 루트에서 생성할 Collection JSON:

```json
{
  "info": {
    "name": "AirConnect Kakao Login",
    "description": "Kakao 로그인 테스트"
  },
  "item": [
    {
      "name": "Kakao 로그인",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"provider\": \"KAKAO\",\n  \"socialToken\": \"{{kakaoAccessToken}}\",\n  \"deviceId\": \"{{deviceId}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/v1/auth/social/login",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "auth", "social", "login"]
        }
      }
    }
  ],
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080"
    },
    {
      "key": "kakaoAccessToken",
      "value": ""
    },
    {
      "key": "deviceId",
      "value": "550e8400-e29b-41d4-a716-446655440000"
    }
  ]
}
```

---

## 🚀 빠른 실행 명령어

### 1️⃣ Backend 실행
```bash
cd /Users/rhee/Library/CloudStorage/OneDrive-개인/대학/졸업프로젝트
./gradlew bootRun
```

### 2️⃣ Postman에서
```
POST http://localhost:8080/api/v1/auth/social/login

{
  "provider": "KAKAO",
  "socialToken": "{카카오_토큰}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 3️⃣ Send 클릭
```
✅ 성공!
```

---

**준비 완료!** 🎉 Kakao accessToken만 있으면 바로 테스트 가능합니다!


