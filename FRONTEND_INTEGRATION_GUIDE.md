# ✅ 프론트엔드 앱 연동 준비 완료 체크리스트

## 🎯 현재 Backend 상태

### ✅ 완전히 준비됨
```
✅ Apple 로그인 엔드포인트: POST /api/v1/auth/social/login
✅ Token 갱신 엔드포인트: POST /api/v1/auth/refresh
✅ 로그아웃 엔드포인트: POST /api/v1/auth/logout
✅ Redis 연결 완료
✅ JSON 역직렬화 수정됨
✅ 빌드 성공
```

---

## 📱 iOS/Android 앱에서 해야 할 일

### 1️⃣ Apple 로그인 SDK 통합 (이미 되어있나요?)

#### iOS (Swift)
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

// Delegate 구현
extension ViewController: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        if let credential = authorization.credential as? ASAuthorizationAppleIDCredential {
            let identityToken = credential.identityToken
            let identityTokenString = String(data: identityToken!, encoding: .utf8)!
            
            // ⭐ 이 값을 Backend로 전송
            sendToBackend(identityToken: identityTokenString)
        }
    }
}
```

#### Android (Kotlin)
```kotlin
// 일반적으로 Sign in with Apple 라이브러리 사용
// https://github.com/willowtreeapps/Sign-In-with-Apple

// identityToken 획득 후
val identityToken = appleIdCredential.identityToken
sendToBackend(identityToken)
```

---

### 2️⃣ Backend로 요청 전송

#### iOS (Swift)
```swift
func sendToBackend(identityToken: String) {
    let deviceId = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
    
    let url = URL(string: "http://your-server.com/api/v1/auth/social/login")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body: [String: Any] = [
        "provider": "APPLE",
        "socialToken": identityToken,
        "deviceId": deviceId
    ]
    
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    
    URLSession.shared.dataTask(with: request) { data, response, error in
        if let data = data,
           let json = try? JSONDecoder().decode(TokenResponse.self, from: data) {
            // 토큰 저장
            KeychainManager.save(accessToken: json.accessToken, key: "accessToken")
            KeychainManager.save(refreshToken: json.refreshToken, key: "refreshToken")
            KeychainManager.save(deviceId: deviceId, key: "deviceId")
            
            DispatchQueue.main.async {
                // 로그인 성공 처리
                self.navigateToHome()
            }
        }
    }.resume()
}

struct TokenResponse: Codable {
    let accessToken: String
    let refreshToken: String
}
```

#### Android (Kotlin)
```kotlin
fun sendToBackend(identityToken: String) {
    val deviceId = UUID.randomUUID().toString()
    
    val body = JSONObject().apply {
        put("provider", "APPLE")
        put("socialToken", identityToken)
        put("deviceId", deviceId)
    }
    
    val request = Request.Builder()
        .url("http://your-server.com/api/v1/auth/social/login")
        .post(RequestBody.create(body.toString().toMediaType()))
        .addHeader("Content-Type", "application/json")
        .build()
    
    client.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessToken = json.getString("accessToken")
                val refreshToken = json.getString("refreshToken")
                
                // SharedPreferences 또는 DataStore에 저장
                PreferenceManager.setAccessToken(accessToken)
                PreferenceManager.setRefreshToken(refreshToken)
                PreferenceManager.setDeviceId(deviceId)
                
                runOnUiThread {
                    // 로그인 성공 처리
                    navigateToHome()
                }
            }
        }
        
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }
    })
}
```

---

### 3️⃣ 토큰 저장 및 관리

#### iOS - Keychain에 저장
```swift
class KeychainManager {
    static func save(accessToken: String, key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: accessToken.data(using: .utf8)!
        ]
        SecItemAdd(query as CFDictionary, nil)
    }
    
    static func retrieve(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
```

#### Android - SharedPreferences 또는 DataStore
```kotlin
class PreferenceManager(context: Context) {
    private val sharedPref = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    
    fun setAccessToken(token: String) {
        sharedPref.edit().putString("accessToken", token).apply()
    }
    
    fun getAccessToken(): String? {
        return sharedPref.getString("accessToken", null)
    }
    
    fun setRefreshToken(token: String) {
        sharedPref.edit().putString("refreshToken", token).apply()
    }
    
    fun getRefreshToken(): String? {
        return sharedPref.getString("refreshToken", null)
    }
}
```

---

### 4️⃣ API 호출 시 Token 첨부

#### iOS (Alamofire 사용)
```swift
import Alamofire

func makeAuthenticatedRequest(url: String, method: HTTPMethod = .get) {
    let accessToken = KeychainManager.retrieve(key: "accessToken") ?? ""
    
    AF.request(url, method: method)
        .responseJSON { response in
            // 응답 처리
        }
}
```

#### Android (Interceptor 사용)
```kotlin
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = PreferenceManager(context).getAccessToken() ?: ""
        
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        
        return chain.proceed(request)
    }
}

// OkHttpClient에 등록
val client = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor(context))
    .build()

val retrofit = Retrofit.Builder()
    .client(client)
    .baseUrl("http://your-server.com")
    .build()
```

---

### 5️⃣ Token 갱신 처리

#### Token 만료 시 자동 갱신
```swift
func refreshAccessToken(completion: @escaping (Bool) -> Void) {
    let refreshToken = KeychainManager.retrieve(key: "refreshToken") ?? ""
    let deviceId = KeychainManager.retrieve(key: "deviceId") ?? ""
    
    let body: [String: Any] = [
        "refreshToken": refreshToken,
        "deviceId": deviceId
    ]
    
    let url = URL(string: "http://your-server.com/api/v1/auth/refresh")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    
    URLSession.shared.dataTask(with: request) { data, response, error in
        if let data = data,
           let json = try? JSONDecoder().decode(TokenResponse.self, from: data) {
            KeychainManager.save(accessToken: json.accessToken, key: "accessToken")
            KeychainManager.save(refreshToken: json.refreshToken, key: "refreshToken")
            completion(true)
        } else {
            completion(false)
        }
    }.resume()
}
```

---

### 6️⃣ 로그아웃

```swift
func logout() {
    let url = URL(string: "http://your-server.com/api/v1/auth/logout")!
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue("Bearer \(KeychainManager.retrieve(key: "accessToken") ?? "")", 
                     forHTTPHeaderField: "Authorization")
    
    let deviceId = KeychainManager.retrieve(key: "deviceId") ?? ""
    let body: [String: Any] = ["deviceId": deviceId]
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)
    
    URLSession.shared.dataTask(with: request) { _, _, _ in
        // 로컬에서 토큰 삭제
        KeychainManager.delete(key: "accessToken")
        KeychainManager.delete(key: "refreshToken")
        KeychainManager.delete(key: "deviceId")
        
        DispatchQueue.main.async {
            self.navigateToLogin()
        }
    }.resume()
}
```

---

## ⚠️ 확인해야 할 사항

### 1. Base URL 설정
```
현재: http://localhost:8080 (개발 환경)
프로덕션: https://your-server.com (배포 후)
```

### 2. 앱 ID 확인
```
Apple 앱 설정에서:
Bundle ID: com.jiin.AirConnect ✅
Sign in with Apple: 활성화됨
```

### 3. HTTPS 필수 (프로덕션)
- Apple은 HTTPS 통신만 허용
- localhost 테스트는 HTTP 가능

### 4. 보안 키 저장
- AccessToken: Keychain (iOS) / Secure Preferences (Android)
- RefreshToken: 같은 위치에 안전하게 저장
- DeviceId: 일반 저장소 가능

---

## 🎯 연동 단계별 가이드

### Step 1️⃣: 개발 환경 테스트
```
1. 로컬에서 Backend 실행 (./gradlew bootRun)
2. iOS/Android 시뮬레이터에서 앱 테스트
3. Base URL을 localhost로 설정
```

### Step 2️⃣: Postman 테스트 (권장)
```
먼저 Backend 엔드포인트가 정상 작동하는지 확인
```

### Step 3️⃣: 앱 연동
```
1. Apple 로그인 버튼 클릭
2. Apple SDK에서 identityToken 획득
3. Backend로 요청 전송
4. accessToken + refreshToken 받기
5. Keychain/SharedPreferences에 저장
```

### Step 4️⃣: 다른 API 호출
```
Authorization: Bearer {accessToken} 헤더 첨부
```

---

## ✅ 최종 체크리스트

- [ ] Apple Sign in with Apple SDK 통합됨
- [ ] identityToken 획득 성공
- [ ] Backend 엔드포인트 연결됨
- [ ] Token 저장 로직 구현됨
- [ ] 자동 Token 갱신 구현됨
- [ ] API 호출 시 Token 첨부됨
- [ ] 로그아웃 로직 구현됨
- [ ] HTTPS 설정 (프로덕션)

---

## 📝 주의사항

### deviceId 관리
- **고정 저장**: 앱 설치 시 1회만 생성
- **변경 금지**: 기기에서 항상 같은 값 사용
- **목적**: 같은 사용자가 여러 기기에서 로그인 시 각각 독립적으로 관리

### Token 보안
- AccessToken: 15분 유효 (HTTP 헤더에 전송 가능)
- RefreshToken: 30일 유효 (안전하게 저장 필수)
- RefreshToken 노출 시 탈취 위험

### 에러 처리
```swift
// 401 Unauthorized: AccessToken 만료 → 갱신 필요
// 403 Forbidden: 사용자 상태 문제
// 500: Backend 에러
```

---

## 🚀 준비 완료!

**Backend:** ✅ 100% 준비됨
**Frontend:** 위의 코드 참고해서 구현하면 됨

이제 바로 앱 상에서 Apple 로그인 가능합니다! 🎉


