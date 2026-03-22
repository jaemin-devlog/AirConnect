# 채팅 API 명세서 (사용 API만)

현재 실제 사용 흐름 기준으로 정리한 채팅 스펙입니다.

## 0. 전제

- 1:1 채팅방 생성은 채팅 API에서 직접 만들지 않고, `matching` 수락 시 생성/연결됩니다.
  - `POST /api/v1/matching/accept/{connectionId}` 응답의 `chatRoomId`를 사용
- 아래 문서는 채팅 도메인에서 실제 사용하는 API만 포함합니다.

## 1. 공통

- REST Base Path: `/api/v1/chat`
- 인증: `Authorization: Bearer {accessToken}`
- 응답 래퍼: `ApiResponse<T>`
- 추적: 응답의 `traceId`

### 성공 응답 예시

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "trace-001"
}
```

### 실패 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-002",
    "message": "권한이 없습니다.",
    "httpStatus": 403,
    "traceId": "trace-err-001",
    "details": null
  },
  "traceId": "trace-err-001"
}
```

---

## 2. REST API

### 2-1) 내 채팅방 목록 조회

- `GET /api/v1/chat/rooms`

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": 123,
      "name": "소개팅 1:1",
      "type": "PERSONAL",
      "connectionId": 14,
      "createdAt": "2026-03-22T19:30:00",
      "latestMessage": "안녕하세요",
      "latestMessageTime": "2026-03-22T19:31:00",
      "unreadCount": 2,
      "targetUserId": 1,
      "targetNickname": "다이아",
      "targetProfileImage": "/api/v1/users/profile-images/1.png"
    }
  ],
  "error": null,
  "traceId": "trace-001"
}
```

### 2-2) 채팅 메시지 조회 (커서)

- `GET /api/v1/chat/rooms/{roomId}/messages?lastMessageId={optional}&size={default:20,max:100}`
- 권한: 해당 room 멤버만

#### Response

```json
{
  "success": true,
  "data": [
    {
      "id": 999,
      "roomId": 123,
      "chatRoomId": 123,
      "senderId": 1,
      "senderNickname": "철수",
      "senderProfileImage": "/profiles/1.png",
      "content": "안녕하세요",
      "message": "안녕하세요",
      "messageType": "TEXT",
      "type": "TEXT",
      "deleted": false,
      "readAt": "2026-03-22T19:32:10",
      "createdAt": "2026-03-22T19:31:55"
    }
  ],
  "error": null,
  "traceId": "trace-002"
}
```

> 하위 호환: `message`/`type` 유지, 신규 클라이언트는 `content`/`messageType` 사용 권장

### 2-3) 메시지 전송

- `POST /api/v1/chat/rooms/{roomId}/messages`

#### Request (`SendMessageRequest`)

```json
{
  "content": "안녕하세요",
  "messageType": "TEXT"
}
```

- `messageType` 생략 시 `TEXT`
- `TEXT`는 빈 문자열 불가

#### Response

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "roomId": 123,
    "chatRoomId": 123,
    "senderId": 10,
    "senderNickname": "민수",
    "senderProfileImage": "/profiles/10.png",
    "content": "안녕하세요",
    "message": "안녕하세요",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": false,
    "readAt": null,
    "createdAt": "2026-03-22T19:33:00"
  },
  "error": null,
  "traceId": "trace-003"
}
```

### 2-4) 메시지 삭제 (소프트 삭제)

- `DELETE /api/v1/chat/rooms/{roomId}/messages/{messageId}`
- 권한: 본인이 보낸 메시지만

#### Response

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "roomId": 123,
    "chatRoomId": 123,
    "senderId": 10,
    "senderNickname": "민수",
    "content": "삭제된 메시지입니다.",
    "message": "삭제된 메시지입니다.",
    "messageType": "TEXT",
    "type": "TEXT",
    "deleted": true,
    "createdAt": "2026-03-22T19:33:00"
  },
  "error": null,
  "traceId": "trace-004"
}
```

### 2-5) 읽음 상태 갱신

- `PATCH /api/v1/chat/rooms/{roomId}/read`
- 설명: 해당 room의 마지막 메시지까지 읽음 포인터 갱신

---

## 3. WebSocket (STOMP)

### 3-1) 연결/구독

- Endpoint: `/ws-stomp`
- CONNECT Header: `Authorization: Bearer {accessToken}`
- SUBSCRIBE: `/sub/chat/room/{roomId}`

### 3-2) 메시지 송신

- SEND Destination: `/pub/chat/message`
- Body (`ChatMessageRequest`)

```json
{
  "roomId": 123,
  "message": "안녕하세요",
  "messageType": "TEXT"
}
```

> `content` 키도 허용되며 내부적으로 `message`와 동일하게 처리됨

### 3-3) 수신 페이로드

- 브로드캐스트는 `ChatMessageResponse` 구조

---

## 4. 주요 에러 코드

- `COMMON-001` (400): 잘못된 요청
- `AUTH-002` (403): 권한 없음 (room 멤버 아님, 제3자 접근)
- `COMMON-999` (500): 서버 내부 오류

---

## 5. 타입

### MessageType

- `TEXT`
- `IMAGE`
- `ENTER`
- `TALK` (legacy)
- `EXIT`

---

## 6. 문서에서 제외한 미사용 API

현재 채팅 기능 문서에서 아래 API는 제외했습니다.

- `POST /api/v1/chat/rooms` (채팅방 직접 생성)
- `POST /api/v1/chat/rooms/{roomId}/join`
- `POST /api/v1/chat/rooms/{roomId}/leave`

필요 시 운영/관리자용 별도 문서로 분리하는 것을 권장합니다.

