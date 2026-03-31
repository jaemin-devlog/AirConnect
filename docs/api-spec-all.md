# AirConnect2 전체 API 명세 (코드 기준)

기준: `src/main/java` 컨트롤러/웹소켓 설정 코드 스캔 결과

- REST Base URL: 서버 환경별 도메인 + 아래 path
- WebSocket(STOMP) Endpoint: `/ws-stomp` (네이티브), `/ws-stomp-sockjs` (SockJS)
- 공통 TraceId: 대부분 REST 응답에 `traceId` 포함

## 1) 공통 응답 포맷

### 1-1. ApiResponse 래핑 사용하는 API

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "4a3f1bf8-6d33-40af-bae8-f490fc811eaf"
}
```

### 1-2. 실패 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청이 올바르지 않습니다.",
    "httpStatus": 400,
    "traceId": "4a3f1bf8-6d33-40af-bae8-f490fc811eaf",
    "details": null
  },
  "traceId": "4a3f1bf8-6d33-40af-bae8-f490fc811eaf"
}
```

### 1-3. 래핑 없이 반환하는 API

- `AuthController` (`/api/v1/auth/**`)는 DTO/HTTP status 직접 반환
- `GMatchingController` (`/api/v1/matching/team-rooms/**`)도 DTO/HTTP status 직접 반환

---

## 2) Auth API

## POST `/api/v1/auth/social/login`

Request
```json
{
  "provider": "KAKAO",
  "socialToken": "kakao-access-or-apple-identity-token",
  "deviceId": "ios-device-uuid"
}
```

Response (200)
```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "user": {
    "userId": 1,
    "provider": "KAKAO",
    "socialId": "kakao-123456",
    "email": "user@example.com",
    "name": "홍길동",
    "deptName": "컴퓨터공학과",
    "nickname": "길동이",
    "studentNum": 202100001,
    "age": 24,
    "status": "ACTIVE",
    "onboardingStatus": "FULL",
    "profileExists": true,
    "profileImageUploaded": true,
    "emailVerified": true,
    "tickets": 100,
    "profile": {
      "userId": 1,
      "height": 178,
      "age": 24,
      "mbti": "INTJ",
      "smoking": "NO",
      "gender": "MALE",
      "military": "COMPLETED",
      "religion": "NONE",
      "residence": "서울",
      "intro": "안녕하세요",
      "instagram": "gildong",
      "profileImagePath": "https://.../api/v1/users/profile-images/a.jpg",
      "updatedAt": "2026-03-29T12:34:56"
    }
  }
}
```

## POST `/api/v1/auth/refresh`

Request
```json
{
  "refreshToken": "jwt-refresh-token",
  "deviceId": "ios-device-uuid"
}
```

Response (200)
```json
{
  "accessToken": "new-access-token",
  "refreshToken": "new-refresh-token"
}
```

## POST `/api/v1/auth/logout`

Request
```json
{
  "deviceId": "ios-device-uuid"
}
```

Response (204)
```json
{}
```

---

## 3) User API

## POST `/api/v1/users/sign-up`

Request
```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "deviceId": "ios-device-uuid",
  "name": "홍길동",
  "nickname": "길동이",
  "studentNum": 202100001,
  "deptName": "컴퓨터공학과",
  "height": 178,
  "age": 24,
  "mbti": "INTJ",
  "smoking": "NO",
  "gender": "MALE",
  "military": "COMPLETED",
  "religion": "NONE",
  "residence": "서울",
  "intro": "안녕하세요",
  "instagram": "gildong"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "status": "ACTIVE",
    "onboardingStatus": "FULL",
    "profileExists": true
  },
  "traceId": "trace-id"
}
```

## GET `/api/v1/users/me`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "provider": "KAKAO",
    "socialId": "kakao-123456",
    "email": "user@example.com",
    "name": "홍길동",
    "deptName": "컴퓨터공학과",
    "nickname": "길동이",
    "studentNum": 202100001,
    "age": 24,
    "status": "ACTIVE",
    "onboardingStatus": "FULL",
    "profileExists": true,
    "profileImageUploaded": true,
    "emailVerified": true,
    "tickets": 98,
    "iosAppAccountToken": "11111111-2222-3333-4444-555555555555",
    "profile": {
      "userId": 1,
      "height": 178,
      "age": 24,
      "mbti": "INTJ",
      "smoking": "NO",
      "gender": "MALE",
      "military": "COMPLETED",
      "religion": "NONE",
      "residence": "서울",
      "intro": "안녕하세요",
      "instagram": "gildong",
      "profileImagePath": "https://.../api/v1/users/profile-images/a.jpg",
      "updatedAt": "2026-03-29T12:34:56"
    }
  },
  "traceId": "trace-id"
}
```

참고: `iosAppAccountToken`은 인증/권한 부여용 토큰이 아니라, iOS 인앱결제 거래를 사용자와 바인딩하기 위한 식별자다.

## GET `/api/v1/users/profile`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "height": 178,
    "age": 24,
    "mbti": "INTJ",
    "smoking": "NO",
    "gender": "MALE",
    "military": "COMPLETED",
    "religion": "NONE",
    "residence": "서울",
    "intro": "안녕하세요",
    "instagram": "gildong",
    "profileImagePath": "https://.../api/v1/users/profile-images/a.jpg",
    "updatedAt": "2026-03-29T12:34:56"
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/users/profile`

Request
```json
{
  "height": 180,
  "age": 25,
  "mbti": "ENTP",
  "smoking": "NO",
  "gender": "MALE",
  "military": "COMPLETED",
  "religion": "NONE",
  "residence": "경기 성남시",
  "intro": "수정된 소개",
  "instagram": "new_insta"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "height": 180,
    "age": 25,
    "mbti": "ENTP",
    "smoking": "NO",
    "gender": "MALE",
    "military": "COMPLETED",
    "religion": "NONE",
    "residence": "경기 성남시",
    "intro": "수정된 소개",
    "instagram": "new_insta",
    "profileImagePath": "https://.../api/v1/users/profile-images/a.jpg",
    "updatedAt": "2026-03-29T12:40:00"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/users/profile-image` (multipart/form-data)

Request(JSON 표기 불가, multipart)
```json
{
  "file": "<binary>"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "imageUrl": "https://.../api/v1/users/profile-images/uuid.jpg"
  },
  "traceId": "trace-id"
}
```

## DELETE `/api/v1/users/me`

Request
```json
{
  "reason": "테스트 탈퇴"
}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## GET `/api/v1/users/profile-images/{fileName}`

Request
```json
{}
```

Response (200)
```json
{
  "binary": "image/jpeg"
}
```

---

## 4) 1:1 Matching API

## GET `/api/v1/matching/recommendations`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "count": 2,
    "candidates": [
      {
        "userId": 126,
        "socialId": "dummy-126",
        "studentNum": 202100126,
        "age": 24,
        "status": "ACTIVE",
        "onboardingStatus": "FULL",
        "profileExists": true,
        "profileImageUploaded": false,
        "emailVerified": false,
        "tickets": 100,
        "nickname": "민수",
        "deptName": "컴퓨터공학과",
        "profile": {
          "userId": 126,
          "height": 178,
          "age": 24,
          "mbti": "INTJ",
          "smoking": "NO",
          "gender": "MALE",
          "military": "COMPLETED",
          "religion": "NONE",
          "residence": "서울",
          "intro": "소개",
          "instagram": "insta",
          "profileImagePath": "https://.../api/v1/users/profile-images/x.jpg",
          "updatedAt": "2026-03-29T12:00:00"
        }
      }
    ],
    "userTicketsRemaining": 76
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/matching/connect/{targetUserId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "chatRoomId": 14,
    "targetUserId": 126,
    "alreadyConnected": false
  },
  "traceId": "trace-id"
}
```

## GET `/api/v1/matching/requests`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "sentCount": 1,
    "receivedCount": 1,
    "sent": [
      {
        "connectionId": 12,
        "userId": 126,
        "socialId": "dummy-126",
        "nickname": "민수",
        "deptName": "컴퓨터공학과",
        "studentNum": 202100126,
        "age": 24,
        "userStatus": "ACTIVE",
        "onboardingStatus": "FULL",
        "profileExists": true,
        "profileImageUploaded": true,
        "emailVerified": true,
        "tickets": 100,
        "profile": {
          "userId": 126,
          "height": 178,
          "age": 24,
          "mbti": "INTJ",
          "smoking": "NO",
          "gender": "MALE",
          "military": "COMPLETED",
          "religion": "NONE",
          "residence": "서울",
          "intro": "소개",
          "instagram": "insta",
          "profileImagePath": "https://.../api/v1/users/profile-images/x.jpg",
          "updatedAt": "2026-03-29T12:00:00"
        },
        "status": "PENDING",
        "requestedAt": "2026-03-29T12:10:00",
        "respondedAt": null
      }
    ],
    "received": []
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/matching/accept/{connectionId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "connectionId": 12,
    "targetUserId": 126,
    "chatRoomId": 14,
    "status": "ACCEPTED"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/matching/reject/{connectionId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "connectionId": 12,
    "targetUserId": 126,
    "chatRoomId": null,
    "status": "REJECTED"
  },
  "traceId": "trace-id"
}
```

---

## 5) Chat REST API

## POST `/api/v1/chat/rooms`

Request
```json
{
  "name": "길동이",
  "type": "PERSONAL",
  "targetUserId": 126
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "id": 14,
    "name": "길동이",
    "type": "PERSONAL",
    "connectionId": 12,
    "createdAt": "2026-03-29T03:00:00.000000Z",
    "latestMessage": null,
    "latestMessageTime": null,
    "unreadCount": 0,
    "targetUserId": 126,
    "targetNickname": "민수",
    "targetProfileImage": "https://.../api/v1/users/profile-images/x.jpg"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/chat/rooms/{roomId}/join`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## POST `/api/v1/chat/rooms/{roomId}/leave`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## GET `/api/v1/chat/rooms`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": [
    {
      "id": 14,
      "name": "민수",
      "type": "PERSONAL",
      "connectionId": 12,
      "createdAt": "2026-03-29T03:00:00.000000Z",
      "latestMessage": "안녕하세요",
      "latestMessageTime": "2026-03-29T03:10:30.123456Z",
      "unreadCount": 2,
      "targetUserId": 126,
      "targetNickname": "민수",
      "targetProfileImage": "https://.../api/v1/users/profile-images/x.jpg"
    }
  ],
  "traceId": "trace-id"
}
```

## GET `/api/v1/chat/rooms/{roomId}/messages?lastMessageId={id}&size={n}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": [
    {
      "eventType": "MESSAGE",
      "id": 101,
      "messageId": 101,
      "roomId": 14,
      "chatRoomId": 14,
      "senderId": 126,
      "senderNickname": "민수",
      "senderProfileImage": "https://.../api/v1/users/profile-images/x.jpg",
      "content": "안녕하세요",
      "message": "안녕하세요",
      "messageType": "TEXT",
      "type": "TEXT",
      "deleted": false,
      "unreadCount": 1,
      "readAt": null,
      "sentAt": "2026-03-29T03:10:30.123456Z",
      "createdAt": "2026-03-29T03:10:30.123456Z"
    }
  ],
  "traceId": "trace-id"
}
```

## POST `/api/v1/chat/rooms/{roomId}/messages`

Request
```json
{
  "content": "메시지 본문",
  "messageType": "TEXT"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "eventType": "MESSAGE",
    "id": 102,
    "messageId": 102,
    "roomId": 14,
    "chatRoomId": 14,
    "senderId": 1,
    "senderNickname": "길동이",
    "senderProfileImage": "https://.../api/v1/users/profile-images/me.jpg",
    "content": "메시지 본문",
    "message": "메시지 본문",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": false,
    "unreadCount": 1,
    "readAt": null,
    "sentAt": "2026-03-29T03:11:00.000000Z",
    "createdAt": "2026-03-29T03:11:00.000000Z"
  },
  "traceId": "trace-id"
}
```

## DELETE `/api/v1/chat/rooms/{roomId}/messages/{messageId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "eventType": "MESSAGE",
    "id": 102,
    "messageId": 102,
    "roomId": 14,
    "chatRoomId": 14,
    "senderId": 1,
    "senderNickname": "길동이",
    "content": "삭제된 메시지입니다.",
    "message": "삭제된 메시지입니다.",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": true,
    "unreadCount": 0,
    "readAt": "2026-03-29T03:12:00.000000Z",
    "sentAt": "2026-03-29T03:11:00.000000Z",
    "createdAt": "2026-03-29T03:11:00.000000Z"
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/chat/rooms/{roomId}/read`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## GET `/api/v1/chat/ops/stomp`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "capturedAt": "2026-03-29T03:20:00Z",
    "inboundTotal": 100,
    "inboundFailures": 2,
    "connectSuccess": 20,
    "connectFailures": 1,
    "subscribeSuccess": 40,
    "subscribeFailures": 1,
    "sideEffectFailures": 3,
    "outboundConnectedFrames": 20,
    "outboundErrorFramesCreated": 1,
    "outboundErrorFrames": 1,
    "inboundFailureByType": {
      "AccessDeniedException": 1,
      "IllegalArgumentException": 1
    }
  },
  "traceId": "trace-id"
}
```

---

## 6) Chat STOMP API

### 6-1. Endpoint
- Native STOMP: `ws://{host}/ws-stomp`
- SockJS: `http://{host}/ws-stomp-sockjs`

### 6-2. CONNECT
Headers (`Authorization` 또는 `authorization` 둘 다 허용)
```json
{
  "Authorization": "Bearer <accessToken>"
}
```

서버 응답 프레임
```json
{
  "command": "CONNECTED",
  "headers": {
    "version": "1.2"
  }
}
```

실패 시 ERROR 프레임 예시
```json
{
  "command": "ERROR",
  "body": "Authorization header is required."
}
```

### 6-3. SEND `/pub/chat/message`

Request payload
```json
{
  "roomId": 14,
  "message": "안녕하세요",
  "messageType": "TEXT"
}
```

구독 destination
- `/sub/chat/room/{roomId}`

MESSAGE 이벤트 payload
```json
{
  "eventType": "MESSAGE",
  "messageId": 103,
  "roomId": 14,
  "senderId": 1,
  "senderNickname": "길동이",
  "content": "안녕하세요",
  "messageType": "TEXT",
  "unreadCount": 1,
  "readAt": null,
  "sentAt": "2026-03-29T03:30:10.100000Z",
  "createdAt": "2026-03-29T03:30:10.100000Z"
}
```

READ_RECEIPT 이벤트 payload
```json
{
  "eventType": "READ_RECEIPT",
  "messageId": 103,
  "roomId": 14,
  "unreadCount": 0,
  "readAt": "2026-03-29T03:31:00.000000Z",
  "sentAt": "2026-03-29T03:31:00.000000Z",
  "createdAt": "2026-03-29T03:31:00.000000Z"
}
```

### 6-4. 그룹매칭 구독
- Subscribe: `/sub/matching/team-room/{teamRoomId}`

---

## 7) Notification API

## GET `/api/v1/notifications?cursorId=&size=&unreadOnly=&type=`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "requestedSize": 20,
    "unreadCount": 5,
    "hasNext": true,
    "nextCursorId": 987,
    "count": 20,
    "items": [
      {
        "notificationId": 1001,
        "userId": 1,
        "type": "CHAT_MESSAGE_RECEIVED",
        "category": "CHAT",
        "title": "민수",
        "body": "안녕하세요",
        "deeplink": "airconnect://chat/rooms/14",
        "actorUserId": 126,
        "imageUrl": "https://.../x.jpg",
        "payload": {
          "roomId": 14
        },
        "read": false,
        "readAt": null,
        "deleted": false,
        "createdAt": "2026-03-29T12:30:00"
      }
    ]
  },
  "traceId": "trace-id"
}
```

## GET `/api/v1/notifications/unread-count`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "unreadCount": 5
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/notifications/{notificationId}/read`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "notificationId": 1001,
    "userId": 1,
    "type": "CHAT_MESSAGE_RECEIVED",
    "category": "CHAT",
    "title": "민수",
    "body": "안녕하세요",
    "deeplink": "airconnect://chat/rooms/14",
    "actorUserId": 126,
    "imageUrl": "https://.../x.jpg",
    "payload": {
      "roomId": 14
    },
    "read": true,
    "readAt": "2026-03-29T12:31:00",
    "deleted": false,
    "createdAt": "2026-03-29T12:30:00"
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/notifications/read-all`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "readCount": 5,
    "unreadCount": 0
  },
  "traceId": "trace-id"
}
```

## DELETE `/api/v1/notifications/{notificationId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## GET `/api/v1/notifications/preferences`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "pushEnabled": true,
    "inAppEnabled": true,
    "matchRequestEnabled": true,
    "matchResultEnabled": true,
    "groupMatchingEnabled": true,
    "chatMessageEnabled": true,
    "milestoneEnabled": true,
    "reminderEnabled": true,
    "quietHoursEnabled": false,
    "quietHoursStart": "23:00:00",
    "quietHoursEnd": "07:00:00",
    "timezone": "Asia/Seoul",
    "createdAt": "2026-03-29T00:00:00",
    "updatedAt": "2026-03-29T00:00:00"
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/notifications/preferences`

Request
```json
{
  "pushEnabled": true,
  "inAppEnabled": true,
  "matchRequestEnabled": true,
  "matchResultEnabled": true,
  "groupMatchingEnabled": true,
  "chatMessageEnabled": true,
  "milestoneEnabled": true,
  "reminderEnabled": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "23:00:00",
  "quietHoursEnd": "07:00:00",
  "timezone": "Asia/Seoul"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "pushEnabled": true,
    "inAppEnabled": true,
    "matchRequestEnabled": true,
    "matchResultEnabled": true,
    "groupMatchingEnabled": true,
    "chatMessageEnabled": true,
    "milestoneEnabled": true,
    "reminderEnabled": true,
    "quietHoursEnabled": true,
    "quietHoursStart": "23:00:00",
    "quietHoursEnd": "07:00:00",
    "timezone": "Asia/Seoul",
    "createdAt": "2026-03-29T00:00:00",
    "updatedAt": "2026-03-29T12:00:00"
  },
  "traceId": "trace-id"
}
```

---

## 8) Push Device/Event API

> 아래 API는 2개 베이스 경로를 동시에 지원
> - `/api/v1/push/devices` 와 `/v1/push/devices`
> - `/api/v1/push/events` 와 `/v1/push/events`

## GET `/api/v1/push/devices`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "count": 1,
    "items": [
      {
        "pushDeviceId": 11,
        "userId": 1,
        "deviceId": "ios-device-uuid",
        "platform": "IOS",
        "provider": "FCM",
        "tokenStatus": "ACTIVE",
        "pushEnabled": true,
        "notificationPermissionGranted": true,
        "active": true,
        "apnsTokenRegistered": true,
        "appVersion": "1.0.0",
        "osVersion": "17.4",
        "locale": "ko-KR",
        "timezone": "Asia/Seoul",
        "lastSeenAt": "2026-03-29T12:00:00",
        "lastTokenRefreshedAt": "2026-03-29T12:00:00",
        "deactivatedAt": null,
        "createdAt": "2026-03-29T12:00:00",
        "updatedAt": "2026-03-29T12:00:00"
      }
    ]
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/push/devices`

Request
```json
{
  "deviceId": "ios-device-uuid",
  "platform": "IOS",
  "provider": "FCM",
  "pushToken": "fcm-token",
  "apnsToken": "apns-token",
  "notificationPermissionGranted": true,
  "appVersion": "1.0.0",
  "osVersion": "17.4",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "lastSeenAt": "2026-03-29T12:00:00"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "pushDeviceId": 11,
    "userId": 1,
    "deviceId": "ios-device-uuid",
    "platform": "IOS",
    "provider": "FCM",
    "tokenStatus": "ACTIVE",
    "pushEnabled": true,
    "notificationPermissionGranted": true,
    "active": true,
    "apnsTokenRegistered": true,
    "appVersion": "1.0.0",
    "osVersion": "17.4",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "lastSeenAt": "2026-03-29T12:00:00",
    "lastTokenRefreshedAt": "2026-03-29T12:00:00",
    "deactivatedAt": null,
    "createdAt": "2026-03-29T12:00:00",
    "updatedAt": "2026-03-29T12:00:00"
  },
  "traceId": "trace-id"
}
```

## PATCH `/api/v1/push/devices/{deviceId}`
## PATCH `/api/v1/push/devices/{deviceId}/permission`

Request
```json
{
  "notificationPermissionGranted": false,
  "lastSeenAt": "2026-03-29T12:10:00"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "pushDeviceId": 11,
    "userId": 1,
    "deviceId": "ios-device-uuid",
    "platform": "IOS",
    "provider": "FCM",
    "tokenStatus": "ACTIVE",
    "pushEnabled": false,
    "notificationPermissionGranted": false,
    "active": true,
    "apnsTokenRegistered": true,
    "appVersion": "1.0.0",
    "osVersion": "17.4",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "lastSeenAt": "2026-03-29T12:10:00",
    "lastTokenRefreshedAt": "2026-03-29T12:00:00",
    "deactivatedAt": null,
    "createdAt": "2026-03-29T12:00:00",
    "updatedAt": "2026-03-29T12:10:00"
  },
  "traceId": "trace-id"
}
```

## DELETE `/api/v1/push/devices/{deviceId}`

Request
```json
{}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## POST `/api/v1/push/events`

Request
```json
{
  "notificationId": "1001",
  "providerMessageId": "fcm-msg-id",
  "eventType": "OPENED",
  "occurredAt": "2026-03-29T12:20:00",
  "deviceId": "ios-device-uuid"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "pushEventId": 77,
    "notificationId": "1001",
    "providerMessageId": "fcm-msg-id",
    "eventType": "OPENED",
    "deviceId": "ios-device-uuid",
    "occurredAt": "2026-03-29T12:20:00",
    "storedAt": "2026-03-29T12:20:01"
  },
  "traceId": "trace-id"
}
```

---

## 9) Verification API

## POST `/api/v1/verification/email/send`

Request
```json
{
  "email": "user@example.com"
}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

## POST `/api/v1/verification/email/verify`

Request
```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "trace-id"
}
```

---

## 10) Group Matching API

> 이 섹션은 `ApiResponse` 래퍼 없이 DTO를 바로 반환합니다.

## POST `/api/v1/matching/team-rooms`

Request
```json
{
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "ageRangeMin": 20,
  "ageRangeMax": 26
}
```

Response (201)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": true,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 1,
  "full": false,
  "status": "OPEN",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 0,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:00:00",
  "members": [
    {
      "userId": 10,
      "nickname": "팀장",
      "leader": true,
      "active": true,
      "ready": false,
      "joinedAt": "2026-03-29T12:00:00",
      "leftAt": null
    }
  ]
}
```

## GET `/api/v1/matching/team-rooms/public?teamSize=TWO`

Request
```json
{}
```

Response (200)
```json
[
  {
    "id": 1,
    "teamName": "우리팀",
    "teamGender": "M",
    "teamSize": "TWO",
    "targetMemberCount": 2,
    "currentMemberCount": 1,
    "status": "OPEN",
    "opponentGenderFilter": "ANY",
    "visibility": "PUBLIC",
    "tempChatRoomId": 101,
    "createdAt": "2026-03-29T12:00:00"
  }
]
```

## POST `/api/v1/matching/team-rooms/{teamRoomId}/join`

Request
```json
{}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": false,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "READY_CHECK",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 0,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:05:00",
  "members": []
}
```

## POST `/api/v1/matching/team-rooms/join-by-invite`

Request
```json
{
  "inviteCode": "ABCD1234"
}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": false,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "READY_CHECK",
  "opponentGenderFilter": "ANY",
  "visibility": "PRIVATE",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": null,
  "ageRangeMax": null,
  "readyMemberCount": 0,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:05:00",
  "members": []
}
```

## GET `/api/v1/matching/team-rooms/me`

Request
```json
{}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": true,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "READY_CHECK",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 2,
  "allMembersReady": true,
  "canStartMatching": true,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:06:00",
  "members": []
}
```

Response (204)
```json
{}
```

## PATCH `/api/v1/matching/team-rooms/{teamRoomId}/ready`

Request
```json
{
  "ready": true
}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": true,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "READY_CHECK",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 2,
  "allMembersReady": true,
  "canStartMatching": true,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:07:00",
  "members": []
}
```

## POST `/api/v1/matching/team-rooms/{teamRoomId}/queue/start`

Request
```json
{}
```

Response (200)
```json
{
  "teamRoomId": 1,
  "status": "QUEUE_WAITING",
  "position": 3,
  "aheadCount": 2,
  "totalWaitingTeams": 7,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}
```

## GET `/api/v1/matching/team-rooms/{teamRoomId}/queue`

Request
```json
{}
```

Response (200)
```json
{
  "teamRoomId": 1,
  "status": "MATCHED",
  "position": 0,
  "aheadCount": 0,
  "totalWaitingTeams": 0,
  "finalGroupRoomId": 91,
  "finalChatRoomId": 501,
  "matched": true
}
```

## POST `/api/v1/matching/team-rooms/{teamRoomId}/queue/leave`

Request
```json
{}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": true,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "READY_CHECK",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 2,
  "allMembersReady": true,
  "canStartMatching": true,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:20:00",
  "members": []
}
```

## POST `/api/v1/matching/team-rooms/{teamRoomId}/leave`

Request
```json
{}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": false,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 1,
  "full": false,
  "status": "OPEN",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 1,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:30:00",
  "members": []
}
```

## DELETE `/api/v1/matching/team-rooms/{teamRoomId}`

Request
```json
{}
```

Response (200)
```json
{
  "id": 1,
  "leaderId": 10,
  "meLeader": true,
  "teamName": "우리팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 0,
  "full": false,
  "status": "CANCELLED",
  "opponentGenderFilter": "ANY",
  "visibility": "PUBLIC",
  "tempChatRoomId": 101,
  "inviteCode": "ABCD1234",
  "ageRangeMin": 20,
  "ageRangeMax": 26,
  "readyMemberCount": 0,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": "2026-03-29T12:40:00",
  "createdAt": "2026-03-29T12:00:00",
  "updatedAt": "2026-03-29T12:40:00",
  "members": []
}
```

## GET `/api/v1/matching/team-rooms/{teamRoomId}/final-room`

Request
```json
{}
```

Response (200)
```json
{
  "id": 91,
  "chatRoomId": 501,
  "team1RoomId": 1,
  "team2RoomId": 2,
  "matchResultId": 333,
  "teamSize": "TWO",
  "finalMemberCount": 4,
  "status": "ACTIVE",
  "createdAt": "2026-03-29T12:50:00",
  "endedAt": null,
  "cancelledAt": null,
  "updatedAt": "2026-03-29T12:50:00"
}
```

Response (204)
```json
{}
```

---

## 11) IAP API

## POST `/api/v1/iap/ios/transactions/verify`

Request
```json
{
  "signedTransactionInfo": "JWS...",
  "transactionId": "2000001234567890",
  "appAccountToken": "server-issued-uuid"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "transactionId": "2000001234567890",
    "productId": "com.airconnect.tickets.pack10",
    "grantStatus": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "ledgerId": "TICKET_LEDGER_90210",
    "processedAt": "2026-03-30T13:00:01Z"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/iap/ios/transactions/sync`

Request
```json
{
  "transactions": [
    { "signedTransactionInfo": "JWS..." },
    { "signedTransactionInfo": "JWS..." }
  ]
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "total": 2,
    "successCount": 1,
    "failureCount": 1,
    "results": [
      {
        "success": true,
        "result": {
          "transactionId": "2000001234567890",
          "productId": "com.airconnect.tickets.pack10",
          "grantStatus": "GRANTED",
          "grantedTickets": 10,
          "beforeTickets": 17,
          "afterTickets": 27,
          "ledgerId": "TICKET_LEDGER_90210",
          "processedAt": "2026-03-30T13:00:01Z"
        }
      },
      {
        "success": false,
        "errorCode": "IAP_INVALID_TRANSACTION",
        "message": "유효하지 않은 거래입니다."
      }
    ]
  },
  "traceId": "trace-id"
}
```

## GET `/api/v1/iap/ios/transactions/{transactionId}`

Response (200)
```json
{
  "success": true,
  "data": {
    "id": 1,
    "userId": 1,
    "store": "APPLE",
    "productId": "com.airconnect.tickets.pack10",
    "transactionId": "2000001234567890",
    "status": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "processedAt": "2026-03-30T13:00:01Z",
    "createdAt": "2026-03-30T13:00:00Z"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/iap/android/purchases/verify`

Request
```json
{
  "productId": "com.airconnect.tickets.pack10",
  "purchaseToken": "token-abc",
  "orderId": "GPA.1234-5678-9012-34567",
  "packageName": "com.airconnect.app",
  "purchaseTime": "2026-03-30T13:00:00Z"
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "purchaseToken": "token-abc",
    "orderId": "GPA.1234-5678-9012-34567",
    "productId": "com.airconnect.tickets.pack10",
    "grantStatus": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "ledgerId": "TICKET_LEDGER_90211",
    "processedAt": "2026-03-30T13:00:01Z"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/iap/android/purchases/sync`

Request
```json
{
  "purchases": [
    {
      "purchaseToken": "token1",
      "productId": "com.airconnect.tickets.pack5",
      "orderId": "GPA...",
      "packageName": "com.airconnect.app"
    }
  ]
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "total": 1,
    "successCount": 1,
    "failureCount": 0,
    "results": []
  },
  "traceId": "trace-id"
}
```

## GET `/api/v1/iap/android/purchases/{purchaseToken}`

Response (200)
```json
{
  "success": true,
  "data": {
    "id": 2,
    "userId": 1,
    "store": "GOOGLE",
    "productId": "com.airconnect.tickets.pack10",
    "purchaseToken": "token-abc",
    "orderId": "GPA.1234-5678-9012-34567",
    "status": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 27,
    "afterTickets": 37,
    "processedAt": "2026-03-30T13:05:01Z",
    "createdAt": "2026-03-30T13:05:00Z"
  },
  "traceId": "trace-id"
}
```

## POST `/api/v1/iap/ios/notifications`
## POST `/api/v1/iap/android/notifications`

Request
```json
{
  "signedPayload": "...or event payload..."
}
```

Response (200)
```json
{
  "success": true,
  "data": {
    "accepted": true
  },
  "traceId": "trace-id"
}
```

---

## 12) Enum 참고값

- `SocialProvider`: `KAKAO`, `APPLE`
- `Gender`: `MALE`, `FEMALE`
- `MilitaryStatus`: `COMPLETED`, `NOT_COMPLETED`, `NOT_APPLICABLE`
- `UserStatus`: `ACTIVE`, `RESTRICTED`, `SUSPENDED`, `DELETED`
- `OnboardingStatus`: `BASIC`, `FULL`
- `ConnectionStatus`: `PENDING`, `ACCEPTED`, `REJECTED`
- `ChatRoomType`: `PERSONAL`, `GROUP`
- `MessageType`: `TEXT`, `IMAGE`, `ENTER`, `TALK`, `EXIT`
- `PushPlatform`: `IOS`, `ANDROID`
- `PushProvider`: `FCM`, `APNS`
- `PushEventType`: `RECEIVED`, `OPENED`
- IAP 관련
  - `IapStore`: `APPLE`, `GOOGLE`
  - `IapOrderStatus`: `PENDING`, `VERIFIED`, `GRANTED`, `REJECTED`, `REFUNDED`, `REVOKED`
  - `GrantStatus`: `GRANTED`, `ALREADY_GRANTED`, `REJECTED`
- `NotificationCategory`: `MATCHING`, `GROUP_MATCHING`, `CHAT`, `MILESTONE`, `REMINDER`, `SYSTEM`
- `NotificationType`: `MATCH_REQUEST_RECEIVED`, `MATCH_REQUEST_ACCEPTED`, `MATCH_REQUEST_REJECTED`, `GROUP_MATCHED`, `CHAT_MESSAGE_RECEIVED`, `MILESTONE_REWARDED`, `TEAM_READY_REQUIRED`, `TEAM_ALL_READY`, `TEAM_ROOM_CANCELLED`, `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_LEFT`, `APPOINTMENT_REMINDER_1H`, `APPOINTMENT_REMINDER_10M`, `SYSTEM_ANNOUNCEMENT`
- 그룹매칭 관련
  - `GTeamGender`: `M`, `F`
  - `GTeamSize`: `TWO`, `THREE`
  - `GGenderFilter`: `M`, `F`, `ANY`
  - `GTeamVisibility`: `PUBLIC`, `PRIVATE`
  - `GTemporaryTeamRoomStatus`: `OPEN`, `READY_CHECK`, `QUEUE_WAITING`, `MATCHED`, `CLOSED`, `CANCELLED`
  - `GFinalGroupRoomStatus`: `ACTIVE`, `ENDED`, `CANCELLED`

