# AirConnect 매칭 API - Postman 테스트 가이드

## 1. 기본 설정

### Environment 변수 설정
Postman에서 `Manage Environments` > `New` 를 클릭하여 다음 변수를 추가:

```json
{
  "baseUrl": "http://localhost:8080",
  "accessToken": "JWT_TOKEN_HERE",
  "userId": "1",
  "targetUserId": "2",
  "connectionId": "1"
}
```

### Authorization 설정
1. 각 요청마다 `Authorization` 탭으로 이동
2. Type: `Bearer Token`
3. Token: `{{accessToken}}`

---

## 2. 테스트 시나리오

### 시나리오: 사용자 A(남)가 사용자 B(여)에게 매칭 요청을 보내고, B가 수락하는 경우

---

## 3. 상세 API 테스트

### 1️⃣ 매칭 시작
**매칭 대기열에 진입합니다**

```http
POST {{baseUrl}}/api/v1/matching/start
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "active": true
  },
  "traceId": "xxx-xxx-xxx"
}
```

---

### 2️⃣ 추천 대상 조회 (1티켓 소모)
**현재 사용자 성별과 다른 성별의 추천 대상 2명을 조회합니다**

```http
GET {{baseUrl}}/api/v1/matching/recommendations
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "count": 2,
    "candidates": [
      {
        "userId": 2,
        "provider": "KAKAO",
        "socialId": "xxx",
        "email": "user2@example.com",
        "name": "김철수",
        "deptName": "소프트웨어학과",
        "nickname": "철수",
        "studentNum": 20240002,
        "status": "ACTIVE",
        "onboardingStatus": "FULL",
        "profileExists": true,
        "profile": {
          "userId": 2,
          "height": 175,
          "mbti": "ISTP",
          "smoking": "NO",
          "gender": "FEMALE",
          "religion": "NONE",
          "residence": "경기",
          "intro": "안녕하세요",
          "instagram": "chulsu_kim",
          "profileImagePath": "http://localhost:8080/api/v1/users/profile-images/xxx.jpg",
          "updatedAt": "2026-03-19T10:00:00"
        }
      },
      {
        "userId": 3,
        "nickname": "영희",
        ...
      }
    ],
    "userTicketsRemaining": 99
  },
  "traceId": "xxx"
}
```

**주의:** 
- 추천 대상은 다음 조건을 만족해야 함
  - 프로필이 있는 사용자
  - 자신과 다른 성별
  - 매칭 활성화 상태
  - 이전에 노출되지 않은 사용자
  - 이미 연결되지 않은 사용자

---

### 3️⃣ 매칭 요청 보내기 (2티켓 소모)
**추천받은 사용자에게 매칭 요청을 보냅니다**

```http
POST {{baseUrl}}/api/v1/matching/connect/2
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "chatRoomId": null,
    "targetUserId": 2,
    "alreadyConnected": false
  },
  "traceId": "xxx"
}
```

**주의:** 
- `2`는 `targetUserId` (요청 대상의 ID)
- 티켓이 2장 이상 있어야 함
- 이미 노출된 사용자에게만 요청 가능
- 처음 요청 시 `chatRoomId`는 `null` (대기 상태)

---

### 4️⃣ 보낸/받은 요청 조회
**사용자가 보낸 요청과 받은 요청을 한 번에 조회합니다**

```http
GET {{baseUrl}}/api/v1/matching/requests
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "sentCount": 1,
    "receivedCount": 1,
    "sent": [
      {
        "connectionId": 10,
        "userId": 2,
        "nickname": "철수",
        "deptName": "소프트웨어학과",
        "studentNum": 20240002,
        "intro": "안녕하세요",
        "mbti": "ISTP",
        "residence": "경기",
        "profileImagePath": "http://localhost:8080/api/v1/users/profile-images/xxx.jpg",
        "status": "PENDING",
        "requestedAt": "2026-03-19T11:00:00",
        "respondedAt": null
      }
    ],
    "received": [
      {
        "connectionId": 11,
        "userId": 3,
        "nickname": "영희",
        "deptName": "컴퓨터공학과",
        "studentNum": 20240003,
        "intro": "안녕합니다",
        "mbti": "INTJ",
        "residence": "서울",
        "profileImagePath": "http://localhost:8080/api/v1/users/profile-images/yyy.jpg",
        "status": "PENDING",
        "requestedAt": "2026-03-19T10:50:00",
        "respondedAt": null
      }
    ]
  },
  "traceId": "xxx"
}
```

**주의:** 
- `sent`: 자신이 보낸 요청 (PENDING 상태만)
- `received`: 자신이 받은 요청 (PENDING 상태만)
- `connectionId`를 메모해두고 수락/거절 시 사용

---

### 5️⃣ 매칭 요청 수락
**받은 요청을 수락하고 채팅방을 생성합니다**

```http
POST {{baseUrl}}/api/v1/matching/requests/11/accept
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**요청 본문:**
```json
{}
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "connectionId": 11,
    "targetUserId": 3,
    "chatRoomId": 50,
    "status": "ACCEPTED"
  },
  "traceId": "xxx"
}
```

**주의:** 
- `11`은 `connectionId` (위 요청 조회 응답에서 받은 값)
- 채팅방이 자동으로 생성됨
- 상태가 `ACCEPTED`로 변경됨
- 수락한 사용자는 매칭 큐에서 자동 제외됨

---

### 6️⃣ 매칭 요청 거절
**받은 요청을 거절합니다 (채팅방 생성 안 함)**

```http
POST {{baseUrl}}/api/v1/matching/requests/11/reject
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**요청 본문:**
```json
{}
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "connectionId": 11,
    "targetUserId": 3,
    "chatRoomId": null,
    "status": "REJECTED"
  },
  "traceId": "xxx"
}
```

**주의:** 
- 채팅방이 생성되지 않음
- 상태가 `REJECTED`로 변경됨

---

### 7️⃣ 매칭 중지
**매칭 대기열에서 퇴출됩니다**

```http
POST {{baseUrl}}/api/v1/matching/stop
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "active": false
  },
  "traceId": "xxx"
}
```

---

## 4. 실제 테스트 흐름

### 🧪 최소 테스트 시나리오 (2명 필요)

#### 사용자 A (남성, ID=1)
```
1. POST /start → 매칭 시작
2. GET /recommendations → 추천 2명 조회 (티켓 1장 소모, 남은 티켓 99)
3. POST /connect/2 → 사용자 B에게 요청 (티켓 2장 소모, 남은 티켓 97)
4. GET /requests → 보낸 요청 확인
```

#### 사용자 B (여성, ID=2)
```
1. POST /start → 매칭 시작
2. GET /recommendations → 추천 2명 조회
3. GET /requests → 받은 요청 확인 (A의 요청)
4. POST /requests/[connectionId]/accept → 요청 수락 (채팅방 생성)
```

---

## 5. 에러 케이스 테스트

### ❌ 매칭 시작 전 추천 요청
```http
GET {{baseUrl}}/api/v1/matching/recommendations
Authorization: Bearer {{accessToken}}
```

**응답 (400):**
```json
{
  "success": false,
  "error": {
    "code": "MATCHING_NOT_STARTED",
    "message": "Matching is not started"
  }
}
```

---

### ❌ 티켓 부족
요청을 5번 이상 보내서 티켓을 소모한 후 요청 시도

```http
POST {{baseUrl}}/api/v1/matching/connect/2
Authorization: Bearer {{accessToken}}
```

**응답 (400):**
```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_TICKETS",
    "message": "Insufficient tickets for matching"
  }
}
```

---

### ❌ 노출되지 않은 사용자에게 요청
추천받지 않은 사용자에게 직접 요청

```http
POST {{baseUrl}}/api/v1/matching/connect/999
Authorization: Bearer {{accessToken}}
```

**응답 (400):**
```json
{
  "success": false,
  "error": {
    "code": "CANDIDATE_NOT_EXPOSED",
    "message": "Target user was not exposed as a candidate"
  }
}
```

---

## 6. 팁 및 주의사항

### ✅ 권장사항
- 테스트 전에 각 사용자가 **프로필을 완성**해야 함 (성별 필수)
- **티켓 개수를 주의**: 
  - 추천: 1티켓
  - 요청: 2티켓
  - 초기: 100티켓
- **환경 변수 활용** - 요청/응답에서 받은 값으로 업데이트
- **로그 확인** - 서버 콘솔에서 로그 레벨 확인

### ⚠️ 주의사항
- 같은 성별끼리는 추천 받을 수 없음
- 이미 요청한 사용자는 다시 요청 불가능
- 수락된 연결은 채팅방으로 이동
- 거절된 연결은 상태만 변경되고 재요청 가능

---

## 7. Postman Collection 예시

Collection JSON으로 한 번에 import하려면:

```json
{
  "info": {
    "name": "AirConnect Matching API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "매칭 시작",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Authorization", "value": "Bearer {{accessToken}}"},
          {"key": "Content-Type", "value": "application/json"}
        ],
        "url": {"raw": "{{baseUrl}}/api/v1/matching/start", "host": ["{{baseUrl}}"], "path": ["api", "v1", "matching", "start"]}
      }
    },
    {
      "name": "추천 조회",
      "request": {
        "method": "GET",
        "header": [{"key": "Authorization", "value": "Bearer {{accessToken}}"}],
        "url": {"raw": "{{baseUrl}}/api/v1/matching/recommendations", "host": ["{{baseUrl}}"], "path": ["api", "v1", "matching", "recommendations"]}
      }
    }
  ]
}
```

---

## 8. 빠른 참조

| 기능 | 메서드 | 경로 | 티켓 | 설명 |
|------|--------|------|------|------|
| 매칭 시작 | POST | `/api/v1/matching/start` | 0 | 대기열 진입 |
| 추천 조회 | GET | `/api/v1/matching/recommendations` | 1 | 2명 추천 |
| 요청 보내기 | POST | `/api/v1/matching/connect/{targetUserId}` | 2 | PENDING 연결 생성 |
| 요청 조회 | GET | `/api/v1/matching/requests` | 0 | 보낸/받은 요청 |
| 요청 수락 | POST | `/api/v1/matching/requests/{connectionId}/accept` | 0 | ACCEPTED + 채팅방 |
| 요청 거절 | POST | `/api/v1/matching/requests/{connectionId}/reject` | 0 | REJECTED |
| 매칭 중지 | POST | `/api/v1/matching/stop` | 0 | 대기열 퇴출 |

