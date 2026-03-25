# AirConnect Chat API Spec (v1) — JSON Examples

- Base URL: `/api/v1/chat`
- Response Envelope: `ApiResponse<T>`

## 공통 응답 래퍼 (ApiResponse)

### Success
```json
{
  "success": true,
  "data": {},
  "traceId": "uuid-string"
}
```

### Error (예시)
> 실제 에러 스키마는 `GlobalExceptionHandler` / `ErrorBody` 정책에 따릅니다.
```json
{
  "success": false,
  "data": null,
  "traceId": "uuid-string",
  "error": {
    "code": "FORBIDDEN",
    "message": "해당 채팅방에 접근할 수 없습니다."
  }
}
```

## 시간 포맷
- 모든 시간은 **ISO8601 + Offset**(예: `+09:00`) 형식으로 내려옵니다.

예:
```json
{
  "createdAt": "2026-03-23T15:12:34+09:00"
}
```

---

# 1) 내 채팅방 목록 조회

- **GET** `/api/v1/chat/rooms`

## Request
```json
{
  "method": "GET",
  "url": "/api/v1/chat/rooms",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": [
    {
      "id": 10,
      "name": "상대닉네임",
      "type": "PERSONAL",
      "connectionId": 14,
      "createdAt": "2026-03-23T15:10:00+09:00",
      "latestMessage": "안녕하세요",
      "latestMessageTime": "2026-03-23T15:12:34+09:00",
      "unreadCount": 2,
      "targetUserId": 136,
      "targetNickname": "23",
      "targetProfileImage": "http://.../profile.jpg"
    }
  ],
  "traceId": "..."
}
```

> NOTE
> - PERSONAL 방의 `name`은 서버에서 **상대 닉네임으로 치환**됩니다.
> - `unreadCount`는 **현재 사용자 기준**입니다.

---

# 2) 채팅 메시지 전송 (REST)

- **POST** `/api/v1/chat/rooms/{roomId}/messages`

## Request
```json
{
  "method": "POST",
  "url": "/api/v1/chat/rooms/10/messages",
  "headers": {
    "Authorization": "Bearer <accessToken>",
    "Content-Type": "application/json"
  },
  "body": {
    "content": "안녕하세요",
    "messageType": "TEXT"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "roomId": 10,
    "chatRoomId": 10,
    "senderId": 1,
    "senderNickname": "내닉네임",
    "senderProfileImage": "http://.../me.jpg",
    "content": "안녕하세요",
    "message": "안녕하세요",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": false,
    "unreadCount": 0,
    "readAt": null,
    "sentAt": "2026-03-23T15:12:34+09:00",
    "createdAt": "2026-03-23T15:12:34+09:00"
  },
  "traceId": "..."
}
```

> NOTE
> - `sentAt`은 현재 구현에서 `createdAt`과 동일 의미(저장 시각)로 제공됩니다.
> - `unreadCount`는 현재 사용자 기준 갱신값을 포함합니다.

---

# 3) 채팅 메시지 목록 조회 (커서 페이징)

- **GET** `/api/v1/chat/rooms/{roomId}/messages?lastMessageId={id}&size={size}`

## Request (첫 페이지)
```json
{
  "method": "GET",
  "url": "/api/v1/chat/rooms/10/messages?size=20",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Request (다음 페이지)
```json
{
  "method": "GET",
  "url": "/api/v1/chat/rooms/10/messages?lastMessageId=1001&size=20",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": [
    {
      "id": 999,
      "roomId": 10,
      "chatRoomId": 10,
      "senderId": 136,
      "senderNickname": "상대닉",
      "senderProfileImage": "http://.../other.jpg",
      "content": "반가워요",
      "message": "반가워요",
      "messageType": "TEXT",
      "type": "TEXT",
      "deleted": false,
      "unreadCount": 0,
      "readAt": "2026-03-23T15:13:00+09:00",
      "sentAt": "2026-03-23T15:12:50+09:00",
      "createdAt": "2026-03-23T15:12:50+09:00"
    }
  ],
  "traceId": "..."
}
```

> NOTE
> - 서버는 내부적으로 DESC로 가져온 뒤 응답을 reverse하여 **오래된 순**으로 내려줍니다.
> - 조회 시점에 읽음 처리(`readAt` 갱신) 및 마지막 읽음 포인터(`lastReadMessageId`) 업데이트가 수행될 수 있습니다.

---

# 4) 메시지 삭제 (소프트 삭제)

- **DELETE** `/api/v1/chat/rooms/{roomId}/messages/{messageId}`

## Request
```json
{
  "method": "DELETE",
  "url": "/api/v1/chat/rooms/10/messages/1001",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": {
    "id": 1001,
    "roomId": 10,
    "chatRoomId": 10,
    "senderId": 1,
    "senderNickname": "내닉네임",
    "senderProfileImage": "http://.../me.jpg",
    "content": "삭제된 메시지입니다.",
    "message": "삭제된 메시지입니다.",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": true,
    "unreadCount": 0,
    "readAt": null,
    "sentAt": "2026-03-23T15:12:34+09:00",
    "createdAt": "2026-03-23T15:12:34+09:00"
  },
  "traceId": "..."
}
```

---

# 5) 읽음 상태 갱신

- **PATCH** `/api/v1/chat/rooms/{roomId}/read`

## Request
```json
{
  "method": "PATCH",
  "url": "/api/v1/chat/rooms/10/read",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "..."
}
```

---

# 6) (GROUP/관리용) 채팅방 생성

> PERSONAL 1:1 방은 매칭 수락 시 서버가 내부적으로 생성하는 것을 권장합니다.

- **POST** `/api/v1/chat/rooms`

## Request (GROUP)
```json
{
  "method": "POST",
  "url": "/api/v1/chat/rooms",
  "headers": {
    "Authorization": "Bearer <accessToken>",
    "Content-Type": "application/json"
  },
  "body": {
    "name": "과팅방",
    "type": "GROUP"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": {
    "id": 20,
    "name": "과팅방",
    "type": "GROUP",
    "connectionId": null,
    "createdAt": "2026-03-23T15:00:00+09:00",
    "latestMessage": null,
    "latestMessageTime": null,
    "unreadCount": 0,
    "targetUserId": null,
    "targetNickname": null,
    "targetProfileImage": null
  },
  "traceId": "..."
}
```

---

# 7) (GROUP 전용) 채팅방 참여

- **POST** `/api/v1/chat/rooms/{roomId}/join`

## Request
```json
{
  "method": "POST",
  "url": "/api/v1/chat/rooms/20/join",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "..."
}
```

---

# 8) 채팅방 나가기

- **POST** `/api/v1/chat/rooms/{roomId}/leave`

## Request
```json
{
  "method": "POST",
  "url": "/api/v1/chat/rooms/20/leave",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": null,
  "traceId": "..."
}
```

---

# 9) 채팅방 상대 상세 정보 조회 (프로필 사진 클릭)

- **GET** `/api/v1/chat/rooms/{roomId}/counterpart-profile`

## Request
```json
{
  "method": "GET",
  "url": "/api/v1/chat/rooms/10/counterpart-profile",
  "headers": {
    "Authorization": "Bearer <accessToken>"
  }
}
```

## Response (200)
```json
{
  "success": true,
  "data": {
    "userId": 136,
    "socialId": "dummy-extra-20260320-23",
    "studentNum": 202200023,
    "age": 24,
    "status": "ACTIVE",
    "onboardingStatus": "FULL",
    "profileExists": true,
    "profileImageUploaded": true,
    "emailVerified": false,
    "tickets": 100,
    "nickname": "23",
    "deptName": "",
    "profile": {
      "userId": 136,
      "height": 172,
      "age": 24,
      "mbti": "ESTJ",
      "smoking": "NO",
      "gender": "MALE",
      "military": "NOT_APPLICABLE",
      "religion": "BUDDHIST",
      "residence": "",
      "intro": ".     23.",
      "instagram": "extra_insta_23",
      "profileImagePath": "http://localhost:8080/api/v1/users/profile-images/realtest_001.jpg",
      "updatedAt": "2026-03-19T16:51:44+09:00"
    }
  },
  "traceId": "..."
}
```

---

# WebSocket(STOMP) (참고)

## SEND (메시지 전송)

- Destination: `/pub/chat/message`

### Request Payload
```json
{
  "roomId": 10,
  "message": "안녕하세요",
  "messageType": "TEXT"
}
```

> NOTE
> - STOMP 수신/구독 destination은 프로젝트 WebSocketConfig/RedisSubscriber 구성에 따라 달라질 수 있습니다.


