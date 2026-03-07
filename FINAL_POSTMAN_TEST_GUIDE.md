# ✅ Apple 로그인 Postman 테스트 최종 가이드

## 🎯 현재 상황

### ✅ 완료됨
1. **Docker Redis 실행 완료**
   ```
   docker run -d -p 6379:6379 --name airconnect-redis redis:7-alpine
   ```
   ✅ 성공

2. **Backend 코드 완성**
   - Apple 로그인 통합 ✅
   - JSON 역직렬화 수정 ✅
   - 빌드 성공 ✅

3. **앱 실행**
   ```bash
   ./gradlew bootRun
   ```
   실행 중...

---

## 🚀 Postman에서 테스트하기

### Step 1: 앱이 실행되었는지 확인

터미널에서:
```bash
# 앱 로그 확인
ps aux | grep bootRun

# 또는 헬스 체크
curl http://localhost:8080/actuator/health
```

응답: `{"status":"UP"}` 또는 유사한 응답이 오면 성공 ✅

---

### Step 2: Postman 설정

**URL:**
```
POST http://localhost:8080/api/v1/auth/social/login
```

**Headers:**
```
Content-Type: application/json
```

**Body (Raw JSON):**
```json
{
  "provider": "APPLE",
  "socialToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExIn0.eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiYXVkIjoiY29tLmppaW4uQWlyQ29ubmVjdCIsInN1YiI6IjAwMTI0Ny5jMWI0ZWE4Njk2N2E0ZmRmOGNlMzM1MjZjYmU5NTJlZi4wNjU2IiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiZXhwIjo5OTk5OTk5OTk5fQ.signature",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### Step 3: Send 버튼 클릭

**성공 응답 (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImV4cCI6MTY0NjY2NjY2Nn0.xxx",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOjEsImRldmljZUlkIjoiNTUwZTg0MDAiLCJleHAiOjE2NDkyNDI2NjZ9.xxx"
}
```

**실패 응답 예시 (500 Internal Server Error):**
- Redis 연결 문제 → Docker Redis 확인
- Token 검증 실패 → Apple 공개키 확인
- 기타 에러 → 서버 로그 확인

---

## 🧪 다른 엔드포인트도 테스트

### Token 갱신
```
POST http://localhost:8080/api/v1/auth/refresh

{
  "refreshToken": "{위에서 받은 refreshToken}",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 로그아웃
```
POST http://localhost:8080/api/v1/auth/logout

Headers:
Authorization: Bearer {accessToken}

Body:
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 🔧 문제 해결

### Q: "connection refused" 에러
```
Unable to connect to Redis
```
**원인:** Redis 컨테이너가 실행 중이 아님
**해결:**
```bash
docker start airconnect-redis
docker ps | grep airconnect-redis
```

### Q: "Unable to connect to localhost:8080"
```
Connection refused
```
**원인:** 앱이 시작되지 않음
**해결:**
```bash
# 앱 재시작
./gradlew bootRun

# 로그 확인
ps aux | grep bootRun
```

### Q: "Invalid token" 에러
```
유효하지 않은 Apple 토큰입니다.
```
**원인:** 토큰이 만료되었거나 형식이 잘못됨
**해결:** 최신 identityToken 사용

---

## 📊 최종 체크리스트

- [ ] Docker Redis 실행 중 확인
  ```bash
  docker ps | grep airconnect-redis
  ```

- [ ] 앱 실행 중 확인
  ```bash
  curl http://localhost:8080/actuator/health
  ```

- [ ] Postman 요청 전송
- [ ] 응답 확인
  - 200 OK + accessToken/refreshToken → 🎉 성공!
  - 500 에러 → 로그 확인

---

## 🎯 성공 시 다음 단계

1. ✅ accessToken/refreshToken 받기
2. ✅ Token으로 다른 API 호출 테스트
3. ✅ RefreshToken으로 새로운 AccessToken 받기
4. ✅ 로그아웃 테스트
5. ✅ 클라이언트 앱에 통합

---

## 📝 중요 정보

### identityToken의 형식
실제 Apple identityToken은 JWT 형식으로:
```
Header: { "alg": "RS256", "kid": "xxx", ... }
Payload: { "iss": "https://appleid.apple.com", "aud": "com.jiin.AirConnect", "sub": "xxxxx", "email": "user@example.com", ... }
Signature: (RS256으로 서명됨)
```

### deviceId
- 고정값 사용 가능 (테스트): `550e8400-e29b-41d4-a716-446655440000`
- 실제 앱: UUID 생성 후 기기에 저장

### 토큰 만료 시간
- AccessToken: 15분
- RefreshToken: 30일

---

## 💡 Postman Collection 추가 팁

Postman에서 Environment 변수 설정:
```
base_url: http://localhost:8080
identityToken: (실제 토큰 붙여넣기)
deviceId: 550e8400-e29b-41d4-a716-446655440000
accessToken: (첫 요청 응답에서 자동 저장)
refreshToken: (첫 요청 응답에서 자동 저장)
```

Pre-request Script로 자동 저장:
```javascript
// 응답에서 토큰 자동 저장
pm.test("Save tokens", function() {
    const response = pm.response.json();
    pm.environment.set("accessToken", response.accessToken);
    pm.environment.set("refreshToken", response.refreshToken);
});
```

---

**최종 상태:** 🎉 **95% 완료! 이제 바로 테스트 가능!**


