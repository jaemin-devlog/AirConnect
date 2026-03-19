# 과팅(다대다 매칭) API 명세서 (v1)

> 팀(2인/3인) 단위 “과팅” 매칭 UX 흐름 기준 (Step 1~4)

## 공통

- Base Path: `/api/v1/matching/team-rooms`
- Auth: 모든 API는 `(Header) Authorization: Bearer {accessToken}` 필요
- TraceId: `(Header) X-Trace-Id`를 요청에 주면 그대로 사용, 없으면 서버가 생성해서 응답 헤더에 내려줌
- Time: `LocalDateTime` ISO-8601 문자열 (예: `2026-03-19T23:11:35`) — 타임존 정보 없음

### 성공/실패 응답 형태(주의)

- **Success(성공)**: 현재 Matching REST 컨트롤러는 `ApiResponse` 래퍼 없이 DTO(JSON) 그대로 반환합니다. (201/200/204)
- **Fail(실패)**: 검증/비즈니스 예외는 `ApiResponse.fail` 형태로 내려올 수 있습니다.

예) Fail 공통 포맷

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 요청입니다.",
    "httpStatus": 400,
    "traceId": "err-001",
    "details": null
  },
  "traceId": "err-001"
}
```

## UX 흐름 요약

- Step 1: 임시 팀방 생성/입장 → 임시 팀 채팅방(`tempChatRoomId`) 유지
- Step 2: 전원 준비 완료 → 방장만 매칭 시작 가능(`/queue/start`)
- Step 3: 팀 단위 큐 대기 → `/queue`로 순번/앞 팀 수 시각화 + (권장) `/sub/matching/team-room/{teamRoomId}` 구독
- Step 4: 매칭 성공 → 최종 그룹 채팅방 생성(`finalChatRoomId`) + 임시방 종료 + 앱이 꺼져있으면 푸시로 복귀 유도(서버는 Redis 이벤트 발행)

---

## WebSocket(STOMP) 공통

- WebSocket Endpoint: `/ws-stomp` (권장)
- STOMP Prefix
  - SEND: `/pub/**`
  - SUBSCRIBE: `/sub/**`
- CONNECT 시 헤더
  - `Authorization: Bearer {accessToken}`

---

## API 목록 (표)

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
      <td><code>/api/v1/matching/team-rooms</code></td>
      <td>Step 1. 임시 팀방 생성(2인/3인) + 팀 임시 채팅방 자동 생성</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Body)
{
  "teamName": "A팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "opponentGenderFilter": "F",
  "visibility": "PUBLIC",
  "ageRangeMin": 20,
  "ageRangeMax": 27
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "id": 10,
  "leaderId": 1,
  "meLeader": true,
  "teamName": "A팀",
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 1,
  "full": false,
  "status": "OPEN",
  "opponentGenderFilter": "F",
  "visibility": "PUBLIC",
  "tempChatRoomId": 1001,
  "inviteCode": "AB12CD34",
  "ageRangeMin": 20,
  "ageRangeMax": 27,
  "readyMemberCount": 0,
  "allMembersReady": false,
  "canStartMatching": false,
  "queueToken": null,
  "queuedAt": null,
  "matchedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-03-19T23:11:35",
  "updatedAt": "2026-03-19T23:11:35",
  "members": [
    {
      "userId": 1,
      "nickname": "철수",
      "leader": true,
      "active": true,
      "ready": false,
      "joinedAt": "2026-03-19T23:11:35",
      "leftAt": null
    }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다." },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(이미 다른 팀방 참여 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "TemporaryTeamRoomStatus": ["OPEN", "READY_CHECK", "QUEUE_WAITING", "MATCHED", "CLOSED", "CANCELLED"],
  "TeamSize": ["TWO(2)", "THREE(3)"],
  "TeamGender": ["M", "F"],
  "GenderFilter": ["M", "F", "ANY"],
  "TeamVisibility": ["PUBLIC", "PRIVATE"]
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/matching/team-rooms/public?teamSize={TWO|THREE}</code></td>
      <td>공개 모집 중인 임시 팀방 목록 조회</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Query) teamSize: TWO | THREE</code></pre>
      </td>
      <td>
        <pre><code class="language-json">[
  {
    "id": 10,
    "teamName": "A팀",
    "teamGender": "M",
    "teamSize": "TWO",
    "targetMemberCount": 2,
    "currentMemberCount": 1,
    "status": "OPEN",
    "opponentGenderFilter": "F",
    "visibility": "PUBLIC",
    "tempChatRoomId": 1001,
    "createdAt": "2026-03-19T23:11:35"
  }
]</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/matching/team-rooms/public",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(teamSize 타입 불일치 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "FULL/종료 방은 제외됨"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/join</code></td>
      <td>공개방 입장</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) teamRoomId: Long</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse (위 create 응답과 동일 스키마)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms/{teamRoomId}/join",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(공개방 아님/정원 초과 등)" },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(이미 다른 팀방 참여 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "statusTransition": "정원 충족 시 READY_CHECK 로 전환"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/matching/team-rooms/join-by-invite</code></td>
      <td>비공개방(초대코드) 입장</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Body)
{ "inviteCode": "AB12CD34" }</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms/join-by-invite",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(inviteCode 누락/공개방인데 초대입장 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "TeamVisibility": "PRIVATE 인 방만 초대코드 입장 가능"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/matching/team-rooms/me</code></td>
      <td>내가 참여 중인 활성 임시 팀방 조회</td>
      <td>Authorization</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">200: TemporaryTeamRoomResponse
204: No Content (참여 중인 방 없음)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/matching/team-rooms/me",
  "errors": []
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "appTip": "앱 시작 시 호출 → teamRoomId 확보/복구 플로우"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>PATCH</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/ready</code></td>
      <td>Step 2. 준비 완료 상태 변경</td>
      <td>Authorization + team member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Body)
{ "ready": true }</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse
// allMembersReady=true &amp; meLeader=true 인 경우 canStartMatching=true</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "PATCH /api/v1/matching/team-rooms/{teamRoomId}/ready",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(READY_CHECK 상태 아님 등)" },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "전원 ready + 방장만 /queue/start 가능"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/queue/start</code></td>
      <td>Step 2. (방장) 매칭 시작 → Redis 큐 등록</td>
      <td>Authorization + leader + allMembersReady</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}
(Path) teamRoomId: Long</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "teamRoomId": 10,
  "status": "QUEUE_WAITING",
  "position": 3,
  "aheadCount": 2,
  "totalWaitingTeams": 5,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms/{teamRoomId}/queue/start",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(READY_CHECK 아님/전원 ready 아님 등)" },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(방장 아님/팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "queueSourceOfTruth": "Redis 큐가 진실의 원천",
  "recommend": "앱은 /queue 폴링 + WS 구독으로 MATCHED 이벤트 수신"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/queue</code></td>
      <td>Step 3. 큐 상태 조회(순번/앞 팀 수/매칭 여부)</td>
      <td>Authorization + team member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "teamRoomId": 10,
  "status": "QUEUE_WAITING",
  "position": 3,
  "aheadCount": 2,
  "totalWaitingTeams": 5,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/matching/team-rooms/{teamRoomId}/queue",
  "errors": [
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "position": "1부터 시작(1이면 맨 앞)",
  "aheadCount": "앞에 남은 팀 수(position-1)"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/queue/leave</code></td>
      <td>Step 3. 매칭 큐 이탈(팀방은 유지)</td>
      <td>Authorization + team member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse (status가 READY_CHECK 등으로 변경)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms/{teamRoomId}/queue/leave",
  "errors": [
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "임시 팀 채팅방(tempChatRoomId)은 그대로 유지"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>POST</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/leave</code></td>
      <td>임시 팀방 나가기(멤버 1명)</td>
      <td>Authorization + team member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "POST /api/v1/matching/team-rooms/{teamRoomId}/leave",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(방장 탈퇴 불가/상태 불가 등)" },
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "팀방 상태(OPEN/READY_CHECK)에서만 멤버 변경 가능"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>DELETE</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}</code></td>
      <td>방장: 임시 팀방 취소(종료)</td>
      <td>Authorization + leader</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">TemporaryTeamRoomResponse (status=CANCELLED)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "DELETE /api/v1/matching/team-rooms/{teamRoomId}",
  "errors": [
    { "httpStatus": 400, "code": "COMMON-001", "message": "잘못된 요청입니다.(방장 아님 등)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "종료된 방은 재입장 불가"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>REST</td>
      <td>GET</td>
      <td><code>/api/v1/matching/team-rooms/{teamRoomId}/final-room</code></td>
      <td>Step 4. 최종 그룹 채팅방(매칭 결과) 조회</td>
      <td>Authorization + team member</td>
      <td>
        <pre><code>(Header) Authorization: Bearer {accessToken}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">200:
{
  "id": 501,
  "chatRoomId": 9001,
  "team1RoomId": 10,
  "team2RoomId": 11,
  "matchResultId": 700,
  "teamSize": "TWO",
  "finalMemberCount": 4,
  "status": "ACTIVE",
  "createdAt": "2026-03-19T23:15:00",
  "endedAt": null,
  "cancelledAt": null,
  "updatedAt": "2026-03-19T23:15:00"
}
204: No Content (아직 매칭 미완료)</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "endpoint": "GET /api/v1/matching/team-rooms/{teamRoomId}/final-room",
  "errors": [
    { "httpStatus": 403, "code": "AUTH-002", "message": "권한이 없습니다.(팀 멤버 아님)" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "FinalGroupRoomStatus": ["ACTIVE", "ENDED", "CANCELLED"],
  "appTip": "finalChatRoomId로 채팅 화면 이동(수락/거절 단계 없음)"
}</code></pre>
      </td>
    </tr>

    <tr>
      <td>WS(STOMP)</td>
      <td>SUBSCRIBE</td>
      <td><code>/sub/matching/team-room/{teamRoomId}</code></td>
      <td>Step 3~4. 매칭 상태 실시간 이벤트 수신</td>
      <td>team member</td>
      <td>
        <pre><code>SUBSCRIBE destination: /sub/matching/team-room/{teamRoomId}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "eventType": "MATCHED",
  "teamRoomId": 10,
  "status": "CLOSED",
  "position": 0,
  "aheadCount": 0,
  "totalWaitingTeams": 0,
  "finalGroupRoomId": 501,
  "finalChatRoomId": 9001,
  "matched": true,
  "occurredAt": "2026-03-19T23:15:00"
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "eventType": ["QUEUE_UPDATED", "STATUS_CHANGED", "MATCHED"],
  "errors": [
    { "type": "STOMP ERROR", "reason": "팀 멤버가 아니면 구독 거부" }
  ]
}</code></pre>
      </td>
      <td>
        <pre><code class="language-json">{
  "note": "매칭 성공 시 MATCHED 이벤트로 finalChatRoomId 제공 → 즉시 이동"
}</code></pre>
      </td>
    </tr>
  </tbody>
</table>

---

## (서버 내부) 푸시 디스패치 이벤트 (Redis Pub/Sub)

> 앱이 꺼져 있어 WebSocket 이벤트를 못 받는 경우를 위해, 서버는 “푸시 발송용 이벤트”를 Redis 채널로 발행합니다.  
> 실제 APNs/FCM 전송은 별도 Push Worker/서버가 이 이벤트를 구독해서 수행하는 형태를 전제로 합니다.

- Channel: `push:dispatch:matching`
- Payload 예시

```json
{
  "type": "MATCHED",
  "userId": 1,
  "deviceId": "DEVICE-001",
  "finalGroupRoomId": 501,
  "finalChatRoomId": 9001,
  "occurredAt": "2026-03-19T23:15:00"
}
```

