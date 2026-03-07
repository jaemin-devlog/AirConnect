# 📱 Postman에서 Apple 로그인 테스트하기

## 🎯 전체 흐름

```
1️⃣ 실제 Apple 토큰 획득
   iOS/Android 앱 또는 Apple 개발자 도구에서 identityToken 추출

2️⃣ Postman에서 테스트
   identityToken을 socialToken으로 변환해서 POST 요청

3️⃣ 응답 확인
   accessToken + refreshToken 받기
```

---

## 방법 1️⃣: 실제 iOS 앱에서 identityToken 획득

### iOS (Swift)
```swift
import AuthenticationServices

@available(iOS 13.0, *)
func appleLogin() {
    let request = ASAuthorizationAppleIDProvider().createRequest()
    request.requestedScopes = [.fullName, .email]
    
    let controller = ASAuthorizationController(authorizationRequests: [request])
    controller.delegate = self
    controller.presentationContextProvider = self
    controller.performRequests()
}

// Delegate 메서드
extension YourViewController: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, 
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
            let identityToken = appleIDCredential.identityToken
            let identityTokenString = String(data: identityToken!, encoding: .utf8)
            
            // ⭐ 이 값을 Postman에서 사용
            print("Identity Token: \(identityTokenString ?? "")")
            
            // Backend로 전송
            sendToBackend(identityToken: identityTokenString ?? "")
        }
    }
}

func sendToBackend(identityToken: String) {
    let url = URL(string: "http://localhost:8080/api/v1/auth/social/login")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body: [String: Any] = [
        "provider": "APPLE",
        "socialToken": identityToken,
        "deviceId": UIDevice.current.identifierForVendor?.uuidString ?? "unknown"
    ]
    
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    
    URLSession.shared.dataTask(with: request) { data, response, error in
        // 응답 처리
    }.resume()
}
```

### Android (Kotlin)
```kotlin
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// Apple 로그인 (Android에선 보통 Sign in with Apple 라이브러리 사용)
// 예: https://github.com/willowtreeapps/Sign-In-with-Apple

fun appleLogin() {
    val appleIDRequest = AppleIdSignInRequest()
    // ...
}

// identityToken 획득 후
val identityToken = appleIdCredential.identityToken  // ⭐ 이 값을 Postman에서 사용

// Backend로 전송
sendToBackend(identityToken)

private fun sendToBackend(identityToken: String) {
    val deviceId = UUID.randomUUID().toString()
    
    val body = """
        {
            "provider": "APPLE",
            "socialToken": "$identityToken",
            "deviceId": "$deviceId"
        }
    """.trimIndent()
    
    // Retrofit 또는 OkHttp로 POST 요청
}
```

---

## 방법 2️⃣: Postman에서 직접 테스트 (테스트용 토큰)

### Step 1: Postman 설정

**URL:**
```
POST http://localhost:8080/api/v1/auth/social/login
```

### Step 2: Headers 설정

```
Content-Type: application/json
```

### Step 3: Body 설정 (Raw JSON)

```json
{
  "provider": "APPLE",
  "socialToken": "{여기에 실제 identityToken 붙여넣기}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Step 4: 요청 전송

```
Click Send 버튼
```

### Step 5: 응답 확인

**성공 응답:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**실패 응답 (example):**
```json
{
  "code": "AUTH_INVALID_APPLE_TOKEN",
  "message": "유효하지 않은 Apple 토큰입니다."
}
```

---

## 방법 3️⃣: Postman Collection으로 자동화

### Postman Collection JSON

다음 내용을 `.json` 파일로 저장하고 Postman에서 Import하세요:

```json
{
  "info": {
    "name": "AirConnect Apple Login",
    "description": "Apple 로그인 테스트 컬렉션",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Apple 로그인",
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
          "raw": "{\n  \"provider\": \"APPLE\",\n  \"socialToken\": \"{{appleIdentityToken}}\",\n  \"deviceId\": \"{{deviceId}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/v1/auth/social/login",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "auth", "social", "login"]
        }
      }
    },
    {
      "name": "Token 갱신",
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
          "raw": "{\n  \"refreshToken\": \"{{refreshToken}}\",\n  \"deviceId\": \"{{deviceId}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/v1/auth/refresh",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "auth", "refresh"]
        }
      }
    },
    {
      "name": "로그아웃",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          },
          {
            "key": "Authorization",
            "value": "Bearer {{accessToken}}"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"deviceId\": \"{{deviceId}}\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/v1/auth/logout",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "auth", "logout"]
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
      "key": "appleIdentityToken",
      "value": ""
    },
    {
      "key": "deviceId",
      "value": "550e8400-e29b-41d4-a716-446655440000"
    },
    {
      "key": "accessToken",
      "value": ""
    },
    {
      "key": "refreshToken",
      "value": ""
    }
  ]
}
```

### Postman Collection 사용법

1. **Import**
   - Postman 앱 열기
   - File → Import
   - 위의 JSON 파일 선택

2. **Environment 변수 설정**
   - `base_url` = `http://localhost:8080`
   - `deviceId` = UUID (고정값)
   - `appleIdentityToken` = 실제 토큰 붙여넣기

3. **순서대로 요청 실행**
   ```
   1️⃣ Apple 로그인
   2️⃣ Token 갱신
   3️⃣ 로그아웃
   ```

---

## 방법 4️⃣: cURL로 테스트

### 터미널에서 실행

```bash
# 1️⃣ Apple 로그인
curl -X POST http://localhost:8080/api/v1/auth/social/login \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "APPLE",
    "socialToken": "{여기에 identityToken 붙여넣기}",
    "deviceId": "550e8400-e29b-41d4-a716-446655440000"
  }'

# 2️⃣ Token 갱신
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "{위에서 받은 refreshToken}",
    "deviceId": "550e8400-e29b-41d4-a716-446655440000"
  }'

# 3️⃣ 로그아웃
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {위에서 받은 accessToken}" \
  -d '{
    "deviceId": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

## 방법 5️⃣: 테스트용 더미 Token 생성

### JWT 토큰 검증 우회 (개발 환경 한정)

⚠️ **주의**: 이는 개발/테스트 환경에서만 사용합니다!

```bash
# online-jwt-generator.io 또는 jwt.io에서 생성

# Header
{
  "kid": "86D3B47",
  "alg": "RS256"
}

# Payload (Apple 형식)
{
  "iss": "https://appleid.apple.com",
  "aud": "com.jiin.AirConnect",
  "exp": 9999999999,
  "iat": 1234567890,
  "sub": "001234.567890abcdef.1234",
  "c_hash": "abc123",
  "email": "user@example.com",
  "email_verified": true,
  "is_private_email": false,
  "auth_time": 1234567890
}

# Secret: 공개키로 검증되는 실제 Apple 공개키 필요
```

⚠️ **실제로는 실제 Apple 토큰이 필요합니다!**

---

## 🚀 실제 테스트 시나리오

### Scenario 1: 첫 로그인 (신규 사용자)

**요청:**
```json
{
  "provider": "APPLE",
  "socialToken": "{identityToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc..."
}
```

**DB 상태:**
- ✅ User 테이블에 신규 사용자 생성
- ✅ provider = APPLE
- ✅ socialId = Apple ID
- ✅ email = Apple 이메일

---

### Scenario 2: 기존 사용자 재로그인

**요청:** (동일한 Apple ID)
```json
{
  "provider": "APPLE",
  "socialToken": "{identityToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc..."
}
```

**DB 상태:**
- ✅ 기존 사용자 조회
- ✅ 새로운 refreshToken 생성

---

### Scenario 3: Token 갱신

**요청:**
```json
{
  "refreshToken": "{refreshToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc..."
}
```

---

### Scenario 4: 다른 디바이스에서 로그인

**요청 1 (Device A):**
```json
{
  "provider": "APPLE",
  "socialToken": "{identityToken}",
  "deviceId": "device-a-uuid"
}
```

**요청 2 (Device B):**
```json
{
  "provider": "APPLE",
  "socialToken": "{identityToken}",
  "deviceId": "device-b-uuid"
}
```

**DB 상태:**
- ✅ Redis에 2개의 RefreshToken 저장
- ✅ userId + deviceId 조합으로 구분
- ✅ 각 디바이스별 독립적인 토큰 관리

---

## 🛠️ Postman Pre-request Script

자동으로 deviceId 생성:

```javascript
// Pre-request Script 탭에 추가

// deviceId 자동 생성 (UUID v4)
if (!pm.environment.get("deviceId")) {
    const uuid = () => {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0,
                v = c == 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    };
    pm.environment.set("deviceId", uuid());
}

// 응답에서 token 자동 저장
pm.test("Save tokens", function() {
    const response = pm.response.json();
    if (response.accessToken) {
        pm.environment.set("accessToken", response.accessToken);
    }
    if (response.refreshToken) {
        pm.environment.set("refreshToken", response.refreshToken);
    }
});
```

---

## ✅ 체크리스트

- [ ] iOS/Android 앱에서 identityToken 추출
- [ ] 토큰 값을 Postman `appleIdentityToken` 변수에 입력
- [ ] `POST /api/v1/auth/social/login` 요청 전송
- [ ] accessToken + refreshToken 응답 확인
- [ ] Token 갱신 테스트
- [ ] 로그아웃 테스트
- [ ] DB에서 사용자 데이터 확인

---

## 🔍 문제 해결

### Q: "유효하지 않은 Apple 토큰입니다" 에러

**원인:**
- identityToken이 만료됨
- 토큰 형식이 잘못됨
- Apple 공개키 갱신 필요

**해결:**
1. 최신 identityToken 다시 획득
2. 앱 ID (`com.jiin.AirConnect`) 확인
3. 서버 로그 확인

### Q: deviceId 에러

**원인:**
- deviceId가 비어있음
- 형식이 잘못됨

**해결:**
- UUID 형식 사용: `550e8400-e29b-41d4-a716-446655440000`

### Q: 로그아웃 401 에러

**원인:**
- Authorization 헤더 누락
- accessToken 형식 잘못

**해결:**
- Header: `Authorization: Bearer {token}`

---

## 📊 테스트 순서도

```
┌──────────────────────────┐
│  1️⃣ Apple 로그인         │
│  POST /auth/social/login │
└────────────┬─────────────┘
             │ accessToken, refreshToken
             ▼
┌──────────────────────────┐
│  2️⃣ API 호출             │
│  Authorization 헤더 사용  │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│  3️⃣ Token 갱신           │
│  POST /auth/refresh      │
└────────────┬─────────────┘
             │ 새로운 accessToken
             ▼
┌──────────────────────────┐
│  4️⃣ 로그아웃             │
│  POST /auth/logout       │
└──────────────────────────┘
```

---

**마지막 팁:** 
- 테스트할 때 Response 탭에서 `Pretty` 선택하면 JSON이 정렬돼서 보기 편함
- `Save Response` 기능으로 응답 저장 가능
- Collection 변수로 token을 자동 관리하면 편함!


