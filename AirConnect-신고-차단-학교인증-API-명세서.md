# AirConnect 신고·차단·학교 인증 마크 API 명세서

- 기준 브랜치: `develop`
- 기준 커밋: `24eb51e` + 학교 인증 마크 응답 확장 작업분
- Base URL: `https://airconnect.cloud`
- 작성일: 2026-09-17
- 대상: Android / iOS

## 1. 변경 요약

학교 인증 마크는 서버가 내려주는 Boolean 값으로 표시한다.

```text
true  -> 학교 인증 마크 표시
false -> 학교 인증 마크 숨김
```

이번 변경으로 다음 화면에서 실제 학교 인증 상태를 받을 수 있다.

| 화면 | 응답 필드 |
|---|---|
| 마이페이지 | `emailVerified` |
| 1:1 추천·요청 | `emailVerified` |
| 그룹 매칭 대기방 멤버 | `members[].emailVerified` |
| 그룹 매칭 멤버 상세 | `emailVerified` |
| 1:1 채팅방 목록 | `targetEmailVerified` |
| 채팅 상대·참여자 프로필 | `emailVerified` |
| 채팅 메시지 | `senderEmailVerified` |

기존 필드를 삭제하거나 이름을 바꾸지 않았다. 새 Boolean 필드만 추가했으므로 구버전 앱은 알 수 없는 필드를 무시하면 기존처럼 동작한다. 학교 이메일 주소 원문은 다른 사용자에게 반환하지 않는다.

## 2. 공통 규칙

### 2.1 인증 헤더

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 2.2 일반 REST 성공 응답

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "trace-id"
}
```

그룹 매칭의 `/api/v1/matching/team-rooms/**` API는 현재 공통 `ApiResponse`로 감싸지 않고 응답 객체를 바로 반환한다. 각 그룹 매칭 예시는 이 차이를 반영한다.

### 2.3 학교 인증 상태 기준

- 인증된 학교 이메일이 계정에 연결되어 있으면 `true`이다.
- 미인증, 인증 정보 없음 또는 학교 이메일 연결이 해제된 상태이면 `false`이다.
- 필드는 `null`이 아닌 Boolean으로 반환한다.
- 인증 완료 직후 현재 화면의 API를 다시 호출해 값을 갱신한다.
- 앱 캐시에 영구 고정하지 말고 로그인, 앱 재진입, 인증 완료 시 서버 값을 다시 읽는다.

## 3. 마이페이지

### 3.1 내 전체 정보

```http
GET /api/v1/users/me
```

호환용 별칭:

```http
GET /api/v1/user/me
```

학교 인증 필드:

```text
data.emailVerified
data.profile.emailVerified
```

마이페이지 상단의 인증 마크는 `data.emailVerified` 사용을 권장한다. `profile`이 아직 생성되지 않아 `null`이어도 최상위 값은 사용할 수 있다.

```json
{
  "success": true,
  "data": {
    "userId": 10,
    "nickname": "에어커넥트",
    "deptName": "항공소프트웨어공학과",
    "studentNum": 26,
    "emailVerified": true,
    "profile": {
      "userId": 10,
      "age": 23,
      "mbti": "ENFP",
      "profileImagePath": "https://airconnect.cloud/api/v1/users/profile-images/example.jpg",
      "emailVerified": true
    }
  },
  "error": null,
  "traceId": "trace-id"
}
```

### 3.2 내 프로필 조회·수정

```http
GET /api/v1/users/profile
PATCH /api/v1/users/profile
```

두 API 모두 다음 위치에 인증 상태를 반환한다.

```text
data.emailVerified
```

```json
{
  "success": true,
  "data": {
    "userId": 10,
    "age": 23,
    "mbti": "ENFP",
    "gender": "FEMALE",
    "profileImagePath": "https://airconnect.cloud/api/v1/users/profile-images/example.jpg",
    "emailVerified": true,
    "updatedAt": "2026-09-17T10:30:00"
  },
  "error": null,
  "traceId": "trace-id"
}
```

## 4. 1:1 매칭

### 4.1 추천 목록

```http
GET /api/v1/matching/recommendations
GET /api/v1/matching/recommendations/same-gender
```

필드 위치:

```text
data.candidates[].emailVerified
data.candidates[].profile.emailVerified
```

카드나 목록에서는 후보 최상위의 `emailVerified` 사용을 권장한다.

```json
{
  "success": true,
  "data": {
    "recommendationRequestId": "request-id",
    "count": 1,
    "candidates": [
      {
        "userId": 123,
        "nickname": "하늘",
        "deptName": "항공관광학과",
        "emailVerified": true,
        "profileExists": true,
        "profileImageUploaded": true,
        "profileImage": "https://airconnect.cloud/example.jpg",
        "profile": {
          "userId": 123,
          "age": 22,
          "mbti": "ISFP",
          "emailVerified": true
        }
      }
    ],
    "userTicketsRemaining": 8
  },
  "error": null,
  "traceId": "trace-id"
}
```

### 4.2 보낸·받은 매칭 요청

```http
GET /api/v1/matching/requests
```

필드 위치:

```text
data.sent[].emailVerified
data.sent[].profile.emailVerified
data.received[].emailVerified
data.received[].profile.emailVerified
```

```json
{
  "success": true,
  "data": {
    "sentCount": 0,
    "receivedCount": 1,
    "sent": [],
    "received": [
      {
        "connectionId": 501,
        "userId": 123,
        "nickname": "하늘",
        "emailVerified": true,
        "profile": {
          "userId": 123,
          "emailVerified": true
        },
        "status": "PENDING"
      }
    ]
  },
  "error": null,
  "traceId": "trace-id"
}
```

## 5. 그룹 매칭

### 5.1 임시 팀방 멤버 목록

다음 API의 `members`에 학교 인증 필드가 포함된다.

```http
POST /api/v1/matching/team-rooms
POST /api/v1/matching/team-rooms/join-by-invite
POST /api/v1/matching/team-rooms/{teamRoomId}/invite-code
GET  /api/v1/matching/team-rooms/me
GET  /api/v1/matching/team-rooms/me/state
POST /api/v1/matching/team-rooms/{teamRoomId}/queue/leave
```

일반 팀방 응답의 필드 위치:

```text
members[].emailVerified
```

`GET /me/state`에서 임시 팀방에 참여 중일 때의 필드 위치:

```text
teamRoom.members[].emailVerified
```

응답은 공통 `data` 래퍼 없이 바로 반환된다.

```json
{
  "id": 700,
  "leaderId": 10,
  "meLeader": true,
  "teamGender": "M",
  "teamSize": "TWO",
  "currentMemberCount": 2,
  "members": [
    {
      "userId": 10,
      "nickname": "에어",
      "profileImage": "profiles/10.jpg",
      "emailVerified": true,
      "leader": true,
      "joinedAt": "2026-09-17T10:00:00",
      "hasEnoughTickets": true
    },
    {
      "userId": 11,
      "nickname": "커넥트",
      "profileImage": "profiles/11.jpg",
      "emailVerified": false,
      "leader": false,
      "joinedAt": "2026-09-17T10:01:00",
      "hasEnoughTickets": true
    }
  ]
}
```

### 5.2 그룹 멤버 상세 프로필

```http
GET /api/v1/matching/team-rooms/{teamRoomId}/members/{targetUserId}/profile
```

필드 위치:

```text
emailVerified
profile.emailVerified
```

이 응답도 공통 `data` 래퍼 없이 바로 반환된다.

```json
{
  "userId": 11,
  "nickname": "커넥트",
  "deptName": "항공운항학과",
  "emailVerified": true,
  "profileExists": true,
  "profileImageUploaded": true,
  "profile": {
    "userId": 11,
    "age": 23,
    "mbti": "INTJ",
    "emailVerified": true
  }
}
```

호출 권한:

- 요청자는 해당 임시 팀방의 현재 참여자여야 한다.
- 대상 사용자도 같은 팀방의 현재 참여자여야 한다.
- 본인 프로필은 이 API로 조회할 수 없다.

## 6. 채팅

### 6.1 채팅방 목록

```http
GET /api/v1/chat/rooms
```

1:1 채팅방 필드 위치:

```text
data[].targetEmailVerified
data[].targetProfile.emailVerified
data[].targetProfile.profile.emailVerified
```

```json
{
  "success": true,
  "data": [
    {
      "id": 900,
      "name": "하늘",
      "type": "PERSONAL",
      "targetUserId": 123,
      "targetNickname": "하늘",
      "targetProfileImage": "profiles/123.jpg",
      "targetEmailVerified": true,
      "targetProfile": {
        "userId": 123,
        "nickname": "하늘",
        "emailVerified": true,
        "profile": {
          "userId": 123,
          "emailVerified": true
        }
      }
    }
  ],
  "error": null,
  "traceId": "trace-id"
}
```

그룹 채팅방에는 단일 상대가 없으므로 `targetUserId`와 `targetProfile`은 `null`, `targetEmailVerified`는 `false`이다. 그룹 멤버별 마크는 참여자 목록 API를 사용한다.

### 6.2 1:1 채팅 상대 상세

```http
GET /api/v1/chat/rooms/{roomId}/counterpart-profile
```

필드 위치:

```text
data.emailVerified
data.profile.emailVerified
```

### 6.3 채팅방 전체 참여자 프로필

```http
GET /api/v1/chat/rooms/{roomId}/participants/profiles
```

필드 위치:

```text
data[].emailVerified
data[].profile.emailVerified
```

1:1과 그룹 채팅방 모두 사용할 수 있다. 그룹 채팅방 참여자 목록에서 각 사용자 이름 옆에 마크를 표시할 때 이 값을 사용한다.

### 6.4 선택한 참여자 요약 프로필

```http
GET /api/v1/chat/rooms/{roomId}/participants/{targetUserId}/profile
```

필드 위치:

```text
data.emailVerified
```

### 6.5 메시지 목록·전송·삭제

```http
GET    /api/v1/chat/rooms/{roomId}/messages?lastMessageId={id}&size=20
POST   /api/v1/chat/rooms/{roomId}/messages
DELETE /api/v1/chat/rooms/{roomId}/messages/{messageId}
```

학교 인증 필드:

```text
GET 응답:    data[].senderEmailVerified
POST 응답:   data.senderEmailVerified
DELETE 응답: data.senderEmailVerified
```

```json
{
  "success": true,
  "data": [
    {
      "eventType": "MESSAGE",
      "messageId": 98765,
      "roomId": 900,
      "senderId": 123,
      "senderNickname": "하늘",
      "senderProfileImage": "profiles/123.jpg",
      "senderEmailVerified": true,
      "content": "안녕하세요",
      "messageType": "TEXT",
      "deleted": false,
      "unreadCount": 1,
      "createdAt": "2026-09-17T05:30:00.000000Z"
    }
  ],
  "error": null,
  "traceId": "trace-id"
}
```

### 6.6 WebSocket/STOMP 실시간 메시지

발신:

```text
/pub/chat/message
```

채팅방 구독:

```text
/sub/chat/room/{roomId}
```

`eventType == "MESSAGE"` 이벤트에도 REST 메시지와 동일한 `senderEmailVerified`가 포함된다.

```json
{
  "eventType": "MESSAGE",
  "messageId": 98765,
  "roomId": 900,
  "senderId": 123,
  "senderNickname": "하늘",
  "senderEmailVerified": true,
  "content": "안녕하세요",
  "messageType": "TEXT"
}
```

`eventType == "READ_RECEIPT"`는 사용자 프로필 메시지가 아니므로 발신자 마크를 표시하지 않는다.

## 7. 사용자 신고

### 7.1 신고 접수

```http
POST /api/v1/moderation/reports
```

```json
{
  "reportedUserId": 123,
  "reportReason": "HARASSMENT",
  "detail": "채팅에서 반복적으로 욕설을 보냈습니다.",
  "sourceType": "CHAT_MESSAGE",
  "sourceId": "98765"
}
```

| 필드 | 필수 | 설명 |
|---|---:|---|
| `reportedUserId` | O | 신고 대상 사용자 ID, 양수 |
| `reportReason` | O | `SPAM`, `HARASSMENT`, `INAPPROPRIATE_CONTENT`, `FRAUD`, `IMPERSONATION`, `HATE_SPEECH`, `SEXUAL_CONTENT`, `OTHER` |
| `detail` | X | 상세 설명, 최대 1,000자 |
| `sourceType` | X | `PROFILE`, `MATCHING_REQUEST`, `CHAT_ROOM`, `CHAT_MESSAGE`, `OTHER` |
| `sourceId` | X | 메시지·채팅방·매칭 요청 식별자, 최대 120자 |

성공: `200 OK`

```json
{
  "success": true,
  "data": {
    "reportId": 501,
    "reporterUserId": 10,
    "reportedUserId": 123,
    "reportReason": "HARASSMENT",
    "detail": "채팅에서 반복적으로 욕설을 보냈습니다.",
    "sourceType": "CHAT_MESSAGE",
    "sourceId": "98765",
    "status": "OPEN",
    "createdAt": "2026-09-17T05:30:00Z"
  },
  "error": null,
  "traceId": "trace-id"
}
```

주요 오류:

| HTTP | 코드 | 조건 |
|---:|---|---|
| 400 | `MOD_REPORT_SELF_NOT_ALLOWED` | 본인 신고 |
| 404 | `MOD_REPORT_TARGET_NOT_FOUND` | 대상 없음 또는 탈퇴 |
| 409 | `MOD_REPORT_DUPLICATE` | 동일 신고가 최근 60분 이내 접수됨 |

신고만으로 자동 차단되지 않는다. 신고 후 차단하는 UI라면 차단 API도 별도로 호출한다.

### 7.2 내 신고 목록

```http
GET /api/v1/moderation/reports/me
```

최신순으로 최대 50개를 `data[]`에 반환한다.

## 8. 사용자 차단

### 8.1 차단

```http
POST /api/v1/moderation/blocks/{blockedUserId}
```

요청 본문은 없다. 이미 차단된 사용자에게 다시 호출해도 `200 OK`이며 `alreadyBlocked`가 `true`이다.

```json
{
  "success": true,
  "data": {
    "blockerUserId": 10,
    "blockedUserId": 123,
    "blockedAt": "2026-09-17T05:35:00Z",
    "alreadyBlocked": false
  },
  "error": null,
  "traceId": "trace-id"
}
```

차단 효과:

- 두 사용자는 추천·매칭 후보에서 제외된다.
- 차단 관계에서는 매칭과 채팅 상호작용이 제한된다.
- 차단한 사용자 화면에서 기존 1:1 채팅방이 숨겨진다.
- 상대방에게 차단 알림은 전송되지 않는다.

### 8.2 차단 해제

```http
DELETE /api/v1/moderation/blocks/{blockedUserId}
```

```json
{
  "success": true,
  "data": {
    "blockerUserId": 10,
    "blockedUserId": 123,
    "removed": true
  },
  "error": null,
  "traceId": "trace-id"
}
```

### 8.3 내 차단 목록

```http
GET /api/v1/moderation/blocks
```

### 8.4 특정 사용자 차단 여부

```http
GET /api/v1/moderation/blocks/{targetUserId}
```

```json
{
  "success": true,
  "data": {
    "blockerUserId": 10,
    "blockedUserId": 123,
    "blocked": true,
    "blockedAt": "2026-09-17T05:35:00Z"
  },
  "error": null,
  "traceId": "trace-id"
}
```

이 API는 현재 사용자가 상대방을 차단했는지만 반환한다. 어느 한쪽에서 차단했든 실제 상호작용은 제한된다.

## 9. 앱 적용 체크리스트

1. 공통 프로필 컴포넌트가 `emailVerified`를 받도록 한다.
2. 채팅방 목록은 `targetEmailVerified`, 메시지는 `senderEmailVerified`를 매핑한다.
3. 그룹 채팅은 `participants/profiles` 응답의 사용자별 `emailVerified`를 사용한다.
4. `READ_RECEIPT` 같은 사용자 프로필이 없는 이벤트에는 마크를 표시하지 않는다.
5. 인증 완료 후 현재 화면 API를 다시 호출한다.
6. 서버가 `false`를 반환하면 기존에 표시 중이던 마크도 즉시 숨긴다.

