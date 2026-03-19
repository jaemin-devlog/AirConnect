# Chat API 명세서 (v1)

> 대상: iOS/Android 네이티브 앱 (브라우저/SockJS 미사용 권장)

## 공통

- Base Path: `/api/v1/chat`
- Auth: 모든 API는 `(Header) Authorization: Bearer {accessToken}` 필요
- TraceId: `(Header) X-Trace-Id`를 요청에 주면 그대로 사용, 없으면 서버가 생성해서 응답 헤더에 내려줌
- Time: `LocalDateTime` ISO-8601 문자열 (예: `2026-03-19T23:11:35`) — 타임존 정보 없음
- REST 응답 래퍼: `ApiResponse<T>`

### REST 공통 응답

**Success**

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "trace-001"
}
```

**Fail**

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH-002",
    "message": "권한이 없습니다.",
    "httpStatus": 403,
    "traceId": "err-001",
    "details": null
  },
  "traceId": "err-001"
}
```

## WebSocket(STOMP) 공통

- WebSocket Endpoint: `/ws-stomp` (권장), `/ws-stomp-sockjs` (브라우저 SockJS 용)
- STOMP Prefix
  - SEND: `/pub/**`
  - SUBSCRIBE: `/sub/**`
- CONNECT 시 헤더
  - `Authorization: Bearer {accessToken}`

---

## API 목록 (표)

> 표의 JSON 예시는 “주요” 필드 위주이며, 실제 값은 상황에 따라 달라질 수 있습니다.

<table>
  <thead>
    <tr>
      <th>Protocol</th>
      <th>Method</th>
      <th>Endpoint</th>
      <th>설명</th>
      <th>권한(Gate)</th>
      <th>주요 Request (Body/Headers)</th>
      <th>주요 Response (Success)</th>
      <th>1열</th>
      <th>비고</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/chat/rooms</code></td>
      <td>채팅방 생성 (PERSONAL은 1:1, GROUP은 그룹)</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Body)
{
  "name": "홍길동",
  "type": "PERSONAL",
  "targetUserId": 42
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": {
    "id": 123,
    "name": "홍길동",
    "type": "PERSONAL",
    "createdAt": "2026-03-19T23:11:35",
    "latestMessage": null,
    "latestMessageTime": null,
    "unreadCount": 0
  },
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/chat/rooms",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다." },
    { "httpStatus": 404, "code": "AUTH_USER_NOT_FOUND", "message": "사용자를 찾을 수 없습니다." },
    { "httpStatus": 403, "code": "USER_DELETED", "message": "삭제된 사용자입니다." },
    { "httpStatus": 403, "code": "USER_SUSPENDED", "message": "정지된 사용자 계정입니다." }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "ChatRoomType": ["PERSONAL", "GROUP"]
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/chat/rooms</code></td>
      <td>내 채팅방 목록 조회</td>
      <td>Authorization + room member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": [
    {
      "id": 123,
      "name": "홍길동",
      "type": "PERSONAL",
      "createdAt": "2026-03-19T23:11:35",
      "latestMessage": "안녕하세요",
      "latestMessageTime": "2026-03-19T23:12:10",
      "unreadCount": 3
    }
  ],
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/chat/rooms",
  "errors": [
    { "httpStatus": 403, "code": "USER_DELETED", "message": "삭제된 사용자입니다." },
    { "httpStatus": 403, "code": "USER_SUSPENDED", "message": "정지된 사용자 계정입니다." }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "정렬": "최신 메시지 시간 내림차순(없으면 createdAt 기준)"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/chat/rooms/{roomId}/messages</code></td>
      <td>채팅방 메시지 조회 (커서 페이지네이션)</td>
      <td>Authorization + room member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) roomId: Long
(Query) lastMessageId?: Long
(Query) size: int (default=20, 1~100)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": [
    {
      "id": 999,
      "roomId": 123,
      "senderId": 1,
      "senderNickname": "철수",
      "senderProfileImage": "/profiles/1.png",
      "message": "안녕하세요",
      "type": "TALK",
      "createdAt": "2026-03-19T23:12:10"
    }
  ],
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/chat/rooms/{roomId}/messages",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다." },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(방 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "MessageType": ["ENTER", "TALK", "EXIT"],
  "Cursor": "lastMessageId 기준으로 과거 방향 조회"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/chat/rooms/{roomId}/join</code></td>
      <td>채팅방 입장(멤버 추가). PERSONAL은 불가</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) roomId: Long</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/chat/rooms/{roomId}/join",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(PERSONAL 참여 불가 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "중복 참여 시에도 200(이미 멤버면 무시)"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/chat/rooms/{roomId}/leave</code></td>
      <td>채팅방 나가기(멤버 제거) + EXIT 시스템 메시지 기록/브로드캐스트</td>
      <td>Authorization + room member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) roomId: Long</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/chat/rooms/{roomId}/leave",
  "errors": [
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(방 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "broadcast": "/sub/chat/room/{roomId} 로 EXIT 타입 메시지 전송"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>PATCH</td>
      <td><code>/api/v1/chat/rooms/{roomId}/read</code></td>
      <td>읽음 처리(마지막 메시지까지 읽음으로 갱신)</td>
      <td>Authorization + room member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) roomId: Long</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "trace-001"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "PATCH /api/v1/chat/rooms/{roomId}/read",
  "errors": [
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(방 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "SUBSCRIBE 시에도 서버가 읽음 갱신을 수행"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>WS(STOMP)</td>
      <td>CONNECT</td>
      <td><code>/ws-stomp</code></td>
      <td>STOMP 연결(인증 세션 생성)</td>
      <td>Authorization</td>
      <td>
        <pre><code>CONNECT headers:
Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code>STOMP CONNECTED frame</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "errors": [
    { "type": "STOMP ERROR", "reason": "Authorization header missing/invalid token" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "앱은 native websocket + STOMP 라이브러리 사용 권장"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>WS(STOMP)</td>
      <td>SUBSCRIBE</td>
      <td><code>/sub/chat/room/{roomId}</code></td>
      <td>채팅방 실시간 수신</td>
      <td>room member</td>
      <td>
        <pre><code>SUBSCRIBE destination: /sub/chat/room/{roomId}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "id": 999,
  "roomId": 123,
  "senderId": 1,
  "senderNickname": "철수",
  "senderProfileImage": "/profiles/1.png",
  "message": "안녕하세요",
  "type": "TALK",
  "createdAt": "2026-03-19T23:12:10"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "errors": [
    { "type": "STOMP ERROR", "reason": "방 멤버가 아니면 구독 거부" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "구독 시 서버가 read 갱신을 수행"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>WS(STOMP)</td>
      <td>SEND</td>
      <td><code>/pub/chat/message</code></td>
      <td>메시지 전송</td>
      <td>room member</td>
      <td>
        <pre><code>SEND destination: /pub/chat/message
(Body)
{
  "roomId": 123,
  "message": "안녕하세요"
}</code></pre>
      </td>
      <td>
        <pre><code>응답 프레임 없음(브로드캐스트로 수신)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "errors": [
    { "type": "STOMP ERROR/Disconnect", "reason": "room member 아님/validation 실패" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "broadcast": "/sub/chat/room/{roomId} 로 TALK 타입 메시지 전송"
}</code></pre>
      </td>
    </tr>
  </tbody>
</table>

