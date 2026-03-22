# 1:1 채팅 API 명세서 (최신)

현재 코드 기준으로 `matching` 수락과 `chat` 동작이 연결된 스펙입니다.

## 0. 공통 규약

- REST Base Path: `/api/v1`
- Auth: 모든 API `Authorization: Bearer {accessToken}` 필요
- 응답 래퍼: `ApiResponse<T>`
- traceId: 응답의 최상위 `traceId`로 반환
- 시간 포맷: `yyyy-MM-dd'T'HH:mm:ss` 또는 LocalDateTime ISO-8601

### 성공 응답 예시

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "4a3f1bf8-6d33-40af-bae8-f490fc811eaf"
}
```

### ���패 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-002",
    "message": "권한이 없습니다.",
    "httpStatus": 403,
    "traceId": "cb9d9ad5-869e-4140-9952-7a7406338d14",
    "details": null
  },
  "traceId": "cb9d9ad5-869e-4140-9952-7a7406338d14"
}
```

---

## 1. 매칭 수락과 채팅방 생성

### 1-1) 요청 수락

- `POST /api/v1/matching/accept/{connectionId}`
- 설명: 수신자(상대방)가 요청 수락 시, 1:1 채팅방을 생성(또는 재사용)하고 `chatRoomId`를 반환
- 권한: 해당 `connection`의 실제 수신자만 가능

#### Response (`MatchingResponseResponse`)

```json
{
  "success": true,
  "data": {
    "connectionId": 14,
    "targetUserId": 1,
    "chatRoomId": 99,
    "status": "ACCEPTED"
  },
  "traceId": "a5fa3a2f-fb77-4363-9ec7-6aa5dc5ca6aa"
}
```

---

## 2. 채팅방 API (REST)

### 2-1) 채팅방 생성

- `POST /api/v1/chat/rooms`
- 설명: 일반 채팅방 생성. `PERSONAL`은 대상 유저 기준으로 기존 방이 있으면 재사용 가능

#### Request

```json
{
  "name": "소개팅 1:1",
  "type": "PERSONAL",
  "targetUserId": 42
}
```

### 2-2) 내 채���방 목록

- `GET /api/v1/chat/rooms`

#### Response (`List<ChatRoomResponse>`)

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
  "traceId": "trace-001"
}
```

### 2-3) 채팅방 메시지 조회

- `GET /api/v1/chat/rooms/{roomId}/messages?lastMessageId={optional}&size={default:20,max:100}`
- 설명: 커서 기반 과거 메시지 조회
- 권한: 해당 room 멤버만

#### Response (`List<ChatMessageResponse>`)

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
  "traceId": "trace-001"
}
```

> 참고: `message`/`type`은 하위 호환을 위해 유지되며, 신규 클라이언트는 `content`/`messageType` 사용 권장

### 2-4) 메시지 전송 (REST)

- `POST /api/v1/chat/rooms/{roomId}/messages`
- 설명: 메시지를 DB 저장 후 Redis broadcast

#### Request (`SendMessageRequest`)

```json
{
  "content": "안녕하세요",
  "messageType": "TEXT"
}
```

- `messageType` 미전달 시 기본값 `TEXT`
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
  "traceId": "trace-002"
}
```

### 2-5) 메시지 소프트 삭제

- `DELETE /api/v1/chat/rooms/{roomId}/messages/{messageId}`
- 설명: 발신자 본인 메시지만 소프트 삭제

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
  "traceId": "trace-003"
}
```

### 2-6) 읽음 갱신

- `PATCH /api/v1/chat/rooms/{roomId}/read`
- 설명: 현재 room의 마지막 메시지 기준으로 읽음 포인터 갱신

---

## 3. 채팅 메시지 전송 (WebSocket / STOMP)

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

> `content` 키도 허용(`@JsonAlias`)되며 내부적으로 `message`와 동일하게 처리됨

### 3-3) 브로드캐스트 페이로드

- 구독자에게 `ChatMessageResponse` 구조로 전송

---

## 4. 에러 코드 가이드 (주요)

- `COMMON-001` (400): 잘못된 요청
- `AUTH-002` (403): 권한 없음 (room 멤버 아님, 제3자 접근 등)
- `COMMON-999` (500): 내부 서버 오류

매칭 도메인 관련 에러는 `MatchingErrorCode`를 따릅니다.

---

## 5. 타입 정의

### ChatRoomType

- `PERSONAL`
- `GROUP`

### MessageType

- `TEXT`
- `IMAGE`
- `ENTER`
- `TALK` (legacy)
- `EXIT`

---

## 6. 보안/운영 메모

- 채팅 본문(content/message)은 로그 출력 금지 정책 유지
- 최종 저장소는 RDB(`chat_messages`)
- Redis publish 실패 시에도 DB 저장 성공이면 요청은 성공 처리

