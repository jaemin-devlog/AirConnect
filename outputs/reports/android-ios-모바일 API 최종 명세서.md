# AirConnect Android/iOS 모바일 API 최종 명세

작성일: 2026-09-11

대상: Android / iOS

범위: 1:1 매칭, 그룹매칭, 메인페이지 통계, 마이페이지 추천인, 축제 쿠폰 등록

이 문서는 지금까지 Android/iOS에 전달한 기능 변경 사항을 최신 백엔드 구현 기준으로 합친 최종본이다. 앱 개발자는 다른 개별 변경 문서 대신 이 문서만 사용한다.

## 1. 공통 규칙

### 1.1 기본 URL과 인증

```text
REST API: {BASE_URL}/api/v1
WebSocket/STOMP: wss://{HOST}/ws-stomp
```

- 그룹매칭, 추천인, 쿠폰 API에는 `Authorization: Bearer {accessToken}`이 필요하다.
- 통계 REST API는 로그인하지 않은 상태에서도 호출할 수 있다.
- STOMP 연결은 로그인한 활성 계정만 가능하다. STOMP `CONNECT` 프레임에 다음 native header를 넣는다.

```text
Authorization: Bearer {accessToken}
```

- Android/iOS 네이티브 앱은 SockJS가 아닌 `/ws-stomp`를 사용한다.
- `Idempotency-Key`는 1:1 추천 조회 2종과 1:1 요청 전송에만 필요하다.
- 그룹매칭, 통계, 추천인, 쿠폰 API에는 `Idempotency-Key`가 필요하지 않다.
- 날짜·시간 문자열은 서버 로컬 날짜·시간을 ISO-8601 형식으로 반환한다.

### 1.2 공통 성공 응답

통계, 추천인, 쿠폰 API는 다음 공통 응답으로 감싼다.

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

그룹매칭 API만 성공 DTO를 JSON 최상위에 바로 반환한다. 그룹매칭 오류는 다른 API와 동일한 공통 오류 형식으로 반환한다.

### 1.3 공통 오류 응답

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 요청입니다.",
    "httpStatus": 400,
    "traceId": "550e8400-e29b-41d4-a716-446655440000",
    "details": null
  },
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

앱은 사용자 문구를 HTTP 상태만으로 판단하지 말고 `error.code`를 기준으로 처리한다. 문의 및 서버 로그 확인을 위해 `traceId`를 보존한다.

| HTTP | 코드 | 앱 처리 |
|---|---|---|
| 400 | `COMMON-001` | 입력값 확인 문구 표시 |
| 401 | `AUTH-001` | 토큰 갱신 또는 재로그인 |
| 403 | `AUTH-002` | 현재 계정 또는 작업 권한이 없음을 표시 |
| 404 | `COMMON-404` | 화면을 닫고 최신 상태 재조회 |
| 503 | `COMMON-503` | 서버 점검 안내 표시 |

---

## 2. 1:1 매칭

### 2.1 변경 핵심

- 추천 조회와 요청 전송에 `Idempotency-Key`를 반드시 보낸다.
- 상대방의 소셜 로그인 ID, 전체 학번, 계정 상태, 보유 티켓은 응답에서 제거됐다.
- 전체 학번 대신 nullable `admissionYear`를 사용한다.
- 보낸 요청을 취소할 수 있다.
- 요청 상태에 `CANCELLED`, `EXPIRED`가 추가됐다.
- 수락·거절·취소의 동일 동작 재시도는 안전하게 기존 결과를 반환한다.

### 2.2 API 목록

모든 API에 로그인 인증이 필요하다.

| 기능 | 메서드 | 경로 | `Idempotency-Key` |
|---|---|---|---|
| 이성 추천 | GET | `/api/v1/matching/recommendations` | 필수 |
| 동성 추천 | GET | `/api/v1/matching/recommendations/same-gender` | 필수 |
| 매칭 요청 전송 | POST | `/api/v1/matching/connect/{targetUserId}` | 필수 |
| 보낸·받은 요청 조회 | GET | `/api/v1/matching/requests` | 불필요 |
| 요청 수락 | POST | `/api/v1/matching/accept/{connectionId}` | 불필요 |
| 요청 거절 | POST | `/api/v1/matching/reject/{connectionId}` | 불필요 |
| 보낸 요청 취소 | DELETE | `/api/v1/matching/requests/{connectionId}` | 불필요 |

### 2.3 `Idempotency-Key` 규칙

```http
Authorization: Bearer {accessToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

- UUID 사용을 권장하며 최대 100자다.
- 추천 화면을 새로 불러오거나 새로운 요청을 보낼 때마다 새 키를 생성한다.
- 네트워크 오류나 타임아웃으로 같은 동작을 재시도할 때는 기존 키를 그대로 사용한다.
- 같은 키를 이성 추천, 동성 추천 또는 서로 다른 상대 요청에 재사용하면 안 된다.

| HTTP | 코드 | 앱 처리 |
|---|---|---|
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 키 생성 후 재시도 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 새 사용자 동작으로 판단되는 경우에만 새 키 생성 |

### 2.4 추천 조회

```http
GET /api/v1/matching/recommendations
GET /api/v1/matching/recommendations/same-gender
```

```json
{
  "success": true,
  "data": {
    "recommendationRequestId": "550e8400-e29b-41d4-a716-446655440000",
    "count": 1,
    "candidates": [
      {
        "userId": 27,
        "admissionYear": 2024,
        "onboardingStatus": "FULL",
        "emailVerified": true,
        "profileExists": true,
        "profileImageUploaded": true,
        "age": 22,
        "nickname": "에어냥",
        "deptName": "항공소프트웨어공학과",
        "profileImage": "https://api.example.com/api/v1/users/profile-images/example.jpg",
        "gender": "FEMALE",
        "profile": {
          "userId": 27,
          "height": 165,
          "age": 22,
          "mbti": "ENFP",
          "smoking": "NON_SMOKER",
          "gender": "FEMALE",
          "military": null,
          "religion": "NONE",
          "residence": "서산",
          "intro": "안녕하세요",
          "instagram": "airconnect",
          "profileImagePath": "https://api.example.com/api/v1/users/profile-images/example.jpg",
          "updatedAt": "2026-09-11T12:00:00"
        }
      }
    ],
    "userTicketsRemaining": 8
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

후보 모델 변경:

| 구분 | 필드 |
|---|---|
| 제거 | `socialId`, `studentNum`, 상대방 `status`, 상대방 `tickets` |
| 추가 | `admissionYear: Integer?` |
| 유지 | `onboardingStatus`, `emailVerified`, `profileExists`, `profileImageUploaded`, `profile.instagram` 등 위 응답 필드 |

- `admissionYear=2024`이면 앱에서 `24학번`으로 표시한다.
- `admissionYear=null`이면 학번 영역을 숨긴다.
- Instagram은 `profile.instagram`을 사용한다.

### 2.5 매칭 요청 전송

```http
POST /api/v1/matching/connect/{targetUserId}
Authorization: Bearer {accessToken}
Idempotency-Key: {UUID}
```

요청 본문은 없다. 새 요청에는 티켓 2장이 사용된다.

```json
{
  "success": true,
  "data": {
    "connectionId": 123,
    "chatRoomId": null,
    "targetUserId": 27,
    "alreadyConnected": false,
    "userTicketsRemaining": 8
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

- `alreadyConnected=false`: 신규 요청이며 `userTicketsRemaining`으로 티켓 UI를 갱신한다.
- `alreadyConnected=true`: 추가 차감 없이 기존 연결을 반환한다. `chatRoomId`가 있으면 기존 채팅방으로 이동할 수 있다.
- 취소·거절·만료되어도 사용한 티켓은 환불되지 않는다.

### 2.6 보낸·받은 요청 조회

```http
GET /api/v1/matching/requests
Authorization: Bearer {accessToken}
```

```json
{
  "success": true,
  "data": {
    "sentCount": 1,
    "receivedCount": 1,
    "sent": [
      {
        "connectionId": 123,
        "userId": 27,
        "nickname": "에어냥",
        "deptName": "항공소프트웨어공학과",
        "admissionYear": 2024,
        "onboardingStatus": "FULL",
        "emailVerified": true,
        "profileExists": true,
        "profileImageUploaded": true,
        "age": 22,
        "profile": {},
        "status": "PENDING",
        "requestedAt": "2026-09-11T12:00:00",
        "respondedAt": null
      }
    ],
    "received": []
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

- 목록에는 현재 응답 가능한 `PENDING` 요청만 반환한다.
- 앱 새로고침 후 사라진 항목은 취소·만료·차단·탈퇴·제재 등으로 더 이상 유효하지 않은 요청이므로 로컬에서도 제거한다.
- 요청 상대 모델에도 `socialId`, `studentNum`, `userStatus`, `tickets`가 없으며 `admissionYear`를 사용한다.

### 2.7 수락·거절·취소

```http
POST   /api/v1/matching/accept/{connectionId}
POST   /api/v1/matching/reject/{connectionId}
DELETE /api/v1/matching/requests/{connectionId}
```

공통 성공 응답:

```json
{
  "success": true,
  "data": {
    "connectionId": 123,
    "targetUserId": 27,
    "chatRoomId": 456,
    "status": "ACCEPTED"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

상태 enum:

```text
PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED
```

| `status` | 앱 처리 |
|---|---|
| `PENDING` | 요청 대기 상태 유지 |
| `ACCEPTED` | `chatRoomId`가 있을 때만 채팅방 이동 |
| `REJECTED` | 요청 카드 제거 |
| `CANCELLED` | 요청 카드 제거 |
| `EXPIRED` | 카드 제거 후 “응답 기한이 지난 요청이에요” 표시 |

- 만료된 요청은 HTTP 200과 `status=EXPIRED`로 반환될 수 있으므로 HTTP 상태만 보고 채팅방으로 이동하면 안 된다.
- 같은 수락·거절·취소를 반복하면 기존 결과를 반환하므로 동일 동작을 안전하게 재시도할 수 있다.
- 이미 처리된 요청에 반대 동작을 수행하면 `INVALID_REQUEST`가 반환된다.
- 요청 취소는 요청을 보낸 사용자만 할 수 있다.
- 요청은 7일 후 만료되며 서버가 수락 시점에도 만료 여부를 다시 확인한다.
- 취소·만료된 요청도 학과 랭킹 집계에서는 빠지지 않는다. 랭킹은 생성된 요청 기록의 송신·수신 횟수를 집계한다.

### 2.8 주요 1:1 오류

| HTTP | 코드 | 의미 |
|---|---|---|
| 400 | `PROFILE_REQUIRED`, `PROFILE_GENDER_REQUIRED` | 프로필 또는 성별 정보 필요 |
| 403 | `MATCHING_RESTRICTED` | 매칭 제한 계정 |
| 400 | `INSUFFICIENT_TICKETS` | 티켓 부족 |
| 400 | `INVALID_TARGET` | 올바르지 않은 상대 |
| 400 | `CANDIDATE_NOT_EXPOSED` | 현재 추천으로 노출되지 않은 상대 |
| 403 | `BLOCKED_USER_INTERACTION` | 차단 관계 사용자 |
| 404 | `CONNECTION_NOT_FOUND` | 요청 정보 없음 |
| 400 | `INVALID_REQUEST` | 권한 또는 현재 요청 상태와 맞지 않는 동작 |
| 400 | `ALREADY_CONNECTED` | 이미 대기 중인 요청 존재 |

---

## 3. 그룹매칭

### 3.1 최종 사용자 흐름

1. 사용자가 `2:2` 또는 `3:3`을 선택해 팀을 만든다.
2. 서버가 생성한 숫자 6자리 초대 코드를 같은 성별 친구에게 공유한다.
3. 친구가 초대 코드를 입력해 같은 팀에 들어온다.
4. 정원이 차고 전원의 티켓이 충분하면 방장만 매칭을 시작한다.
5. 팀원 전원이 실시간 대기 순번을 확인할 수 있다.
6. 매칭 중지는 모든 팀원이 할 수 있다.
7. 반대 성별의 같은 인원 팀과 성사되면 최종 그룹 채팅방으로 이동한다.

앱에서 다음 기능은 제거한다.

- 공개방·비공개방 선택
- 방 목록과 모르는 팀 참가
- 팀 이름 입력
- 상대 성별 선택
- 준비 완료
- 매칭 전 임시 채팅

대기방 제목은 별도 서버 필드가 없다. 앱에서 `teamSize`에 따라 `2:2 대기방` 또는 `3:3 대기방`으로 표시한다.

### 3.2 enum 및 핵심 규칙

| 항목 | 값 | 의미 |
|---|---|---|
| `teamSize` | `TWO` | 2:2, 팀원별 필요 티켓 2장 |
| `teamSize` | `THREE` | 3:3, 팀원별 필요 티켓 3장 |
| `teamGender` | `M` | 남성 팀 |
| `teamGender` | `F` | 여성 팀 |

- 팀 성별은 방 생성자의 프로필 성별로 서버가 결정한다.
- 같은 팀에는 같은 성별 사용자만 입장할 수 있다.
- 초대 코드는 숫자 6자리 문자열이다. `012345`처럼 앞자리 `0`을 보존한다.
- 팀을 생성하거나 참가한 사용자는 동시에 다른 활성 임시 팀에 참여할 수 없다.
- 티켓은 대기 시작 시 확인하고 실제 매칭 성사 시 각 팀원에게서 차감한다.

### 3.3 API 목록

기본 경로:

```text
/api/v1/matching/team-rooms
```

| 기능 | 메서드 | 경로 | 요청 | 성공 |
|---|---|---|---|---|
| 팀 생성 | POST | `/api/v1/matching/team-rooms` | `{"teamSize":"TWO"}` | 201 팀 응답 |
| 초대 코드 입장 | POST | `/api/v1/matching/team-rooms/join-by-invite` | `{"inviteCode":"012345"}` | 200 팀 응답 |
| 현재 상태 복구 | GET | `/api/v1/matching/team-rooms/me/state` | 없음 | 200 상태 응답 |
| 내 활성 임시 팀 | GET | `/api/v1/matching/team-rooms/me` | 없음 | 200 팀 응답, 없으면 204 |
| 초대 코드 재발급 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/invite-code` | 없음 | 200 팀 응답 |
| 매칭 시작 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/queue/start` | 없음 | 200 대기 응답 |
| 대기 상태 조회 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/queue` | 없음 | 200 대기 응답 |
| 매칭 중지 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/queue/leave` | 없음 | 200 팀 응답 |
| 팀 나가기 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/leave` | 없음 | 204 |
| 팀 해산 | DELETE | `/api/v1/matching/team-rooms/{teamRoomId}` | 없음 | 204 |
| 팀원 내보내기 | DELETE | `/api/v1/matching/team-rooms/{teamRoomId}/members/{userId}` | 없음 | 200 팀 응답 |
| 팀원 프로필 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/members/{userId}/profile` | 없음 | 200 1:1 후보 프로필 응답 |
| 최종 그룹방 조회 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/final-room` | 없음 | 200 최종방 응답, 없으면 204 |

팀 생성 요청이 타임아웃되면 바로 다시 생성하지 말고 `GET /me/state`로 생성 여부를 먼저 확인한다.

### 3.4 팀 응답

```json
{
  "id": 101,
  "leaderId": 10,
  "meLeader": true,
  "teamGender": "M",
  "teamSize": "TWO",
  "targetMemberCount": 2,
  "currentMemberCount": 2,
  "full": true,
  "status": "OPEN",
  "inviteCode": "012345",
  "inviteShareUrl": "airconnect://matching/join?inviteCode=012345",
  "canStartMatching": true,
  "canStopMatching": false,
  "canLeave": false,
  "canDisband": true,
  "requiredTickets": 2,
  "myTickets": 5,
  "allMembersHaveEnoughTickets": true,
  "queuedAt": null,
  "createdAt": "2026-09-11T13:00:00",
  "updatedAt": "2026-09-11T13:01:00",
  "members": [
    {
      "userId": 10,
      "nickname": "에어냥",
      "profileImage": "https://api.example.com/api/v1/users/profile-images/10/example.jpg",
      "leader": true,
      "joinedAt": "2026-09-11T13:00:00",
      "hasEnoughTickets": true
    }
  ]
}
```

앱은 권한을 자체 계산하지 말고 다음 필드로 버튼을 제어한다.

| 필드 | `true`일 때 표시할 작업 |
|---|---|
| `canStartMatching` | 매칭 시작 |
| `canStopMatching` | 매칭 중지 |
| `canLeave` | 팀 나가기 |
| `canDisband` | 팀 해산 |

- `requiredTickets`는 현재 팀에서 사용자 한 명에게 필요한 티켓 수다.
- 다른 팀원의 실제 티켓 잔액은 제공하지 않는다. `members[].hasEnoughTickets`만 사용한다.
- 프로필 이미지는 `null`일 수 있다.

### 3.5 앱 상태 복구

앱 실행, 그룹매칭 화면 재진입, 앱 foreground 복귀, STOMP 재연결 시 호출한다.

```http
GET /api/v1/matching/team-rooms/me/state
Authorization: Bearer {accessToken}
```

```json
{
  "state": "TEMPORARY_TEAM_ROOM",
  "teamRoom": {},
  "queueSnapshot": null,
  "finalRoom": null,
  "matchingSubscriptionDestination": "/sub/matching/team-room/101"
}
```

| `state` | 앱 화면 |
|---|---|
| `IDLE` | 2:2·3:3 생성 및 초대 코드 입력 화면 |
| `TEMPORARY_TEAM_ROOM` | `teamRoom.status`에 맞는 팀 구성 또는 대기 화면 |
| `FINAL_GROUP_CHAT_ROOM` | `finalRoom.chatRoomId`의 그룹 채팅방 |

`teamRoom.status` 처리:

| 상태 | 앱 처리 |
|---|---|
| `OPEN` | 팀원·초대 코드 및 서버 `can*` 값에 맞는 버튼 표시 |
| `READY_CHECK` | 이전 데이터 호환 상태이므로 `OPEN`과 동일하게 처리 |
| `QUEUE_WAITING` | 대기 순번 및 매칭 중지 버튼 표시 |
| `MATCHED` | 최종 채팅 생성 중 표시, 조작 버튼 숨김 |
| `CLOSED`, `CANCELLED` | 현재 화면 종료 후 `/me/state` 재조회 |

### 3.6 대기 순번

```json
{
  "teamRoomId": 101,
  "status": "QUEUE_WAITING",
  "position": 3,
  "aheadCount": 2,
  "totalWaitingTeams": 5,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}
```

- `position`은 1부터 시작한다.
- `aheadCount`는 내 팀 앞에 있는 팀 수다.
- `totalWaitingTeams`는 같은 인원수와 같은 성별로 대기하는 팀의 수다.
- 반대 성별 팀은 매칭 상대이므로 내 앞 순번에 포함하지 않는다.
- 대기열 진입·이탈·매칭으로 순서가 바뀌면 남은 모든 팀에 새 순번이 실시간 발행된다.
- `matched=true`이고 `finalChatRoomId`가 존재하면 최종 그룹 채팅방으로 이동한다.
- 비대기 상태에서는 순번 필드가 `0`일 수 있다. 화면 상태는 반드시 `status`와 `matched`로 판단한다.

### 3.7 그룹매칭 실시간 구독

구독 경로는 `/me/state`의 `matchingSubscriptionDestination`을 우선 사용한다.

```text
/sub/matching/team-room/{teamRoomId}
```

```json
{
  "eventType": "QUEUE_UPDATED",
  "teamRoomId": 101,
  "status": "QUEUE_WAITING",
  "position": 2,
  "aheadCount": 1,
  "totalWaitingTeams": 4,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false,
  "occurredAt": "2026-09-11T13:02:00"
}
```

| `eventType` | 앱 처리 |
|---|---|
| `QUEUE_UPDATED` | 대기 순번을 payload 값으로 즉시 교체 |
| `STATUS_CHANGED` | `GET /me/state` 재조회 |
| `MATCHED` | `finalChatRoomId`가 있으면 최종 그룹 채팅방으로 이동 |

권장 연결 순서:

1. STOMP 연결
2. 팀 구독
3. `GET /me/state` 호출로 현재 상태 동기화
4. 이후 이벤트로 갱신
5. 연결이 끊기면 재연결·재구독 후 `/me/state` 재호출

WebSocket이 주 갱신 수단이다. 화면 최초 진입·foreground 복귀·재연결 때 REST를 반드시 사용하고, 필요하면 10~15초 간격 REST 조회를 보조 수단으로 둘 수 있다.

### 3.8 최종 그룹 채팅방 응답

```json
{
  "id": 301,
  "chatRoomId": 801,
  "team1RoomId": 101,
  "team2RoomId": 202,
  "matchResultId": 501,
  "teamSize": "TWO",
  "finalMemberCount": 4,
  "status": "ACTIVE",
  "createdAt": "2026-09-11T13:05:00",
  "endedAt": null,
  "cancelledAt": null,
  "updatedAt": "2026-09-11T13:05:00"
}
```

`status` 값은 `ACTIVE`, `ENDED`, `CANCELLED`다. 채팅 화면 이동에는 `id`가 아닌 `chatRoomId`를 사용한다.

### 3.9 주요 그룹매칭 오류

| HTTP | 코드 | 의미 |
|---|---|---|
| 404 | `GMATCH-001` | 팀방 없음 |
| 404 | `GMATCH-002` | 팀원 없음 |
| 400 | `GMATCH-004` | 유효하지 않은 초대 코드 |
| 400 | `GMATCH-005` | 초대 코드 누락 |
| 400 | `GMATCH-008` | 팀 정원 초과 |
| 400 | `GMATCH-009` | 현재 상태에서 입장 불가 |
| 400 | `GMATCH-010` | 종료된 팀방 |
| 400 | `GMATCH-012` | 방장은 나가기 대신 해산 필요 |
| 400 | `GMATCH-013` | 이미 해당 팀의 활성 팀원 |
| 409 | `GMATCH-014` | 이미 다른 활성 팀에 참여 중 |
| 403 | `GMATCH-015` | 해당 팀의 활성 팀원이 아님 |
| 403 | `GMATCH-016` | 팀방 접근 권한 없음 |
| 400 | `GMATCH-018` | 프로필 성별 미설정 |
| 403 | `GMATCH-019` | 팀 성별과 사용자 성별 불일치 |
| 400 | `GMATCH-020`, `GMATCH-021` | 팀 인원 값 누락 또는 미지원 |
| 400 | `GMATCH-022` | 현재 팀방 상태에서 처리 불가 |
| 403 | `GMATCH-023` | 방장 전용 작업 |
| 400 | `GMATCH-024` | 팀 정원이 차지 않음 |
| 400 | `GMATCH-026` | 매칭 대기 상태가 아님 |

상태 충돌 오류를 받으면 임의로 로컬 상태를 고치지 말고 `GET /me/state`를 재호출한다.

### 3.10 제거된 API와 필드

앱에서 다음 API 호출을 제거한다.

```text
GET    /api/v1/matching/team-rooms/public
GET    /api/v1/matching/team-rooms/recruitable
POST   /api/v1/matching/team-rooms/{teamRoomId}/join
PATCH  /api/v1/matching/team-rooms/{teamRoomId}/visibility
PATCH  /api/v1/matching/team-rooms/{teamRoomId}/ready
GET    /api/v1/matching/team-rooms/{teamRoomId}/chat/messages
POST   /api/v1/matching/team-rooms/{teamRoomId}/chat/messages
PATCH  /api/v1/matching/team-rooms/{teamRoomId}/chat/read
```

앱 모델에서 다음 이전 필드를 제거한다.

```text
teamName
visibility
opponentGenderFilter
tempChatRoomId
readyMemberCount
allMembersReady
queueToken
members[].ready
```

---

## 4. 메인페이지 통계

### 4.1 메인 통계 조회

인증 없이 호출할 수 있다.

```http
GET /api/v1/statistics/main
```

```json
{
  "success": true,
  "data": {
    "totalRegisteredUsers": 135,
    "dailyActiveUsers": 34,
    "onlineUserCount": 17,
    "genderRatio": {
      "maleUsers": 70,
      "femaleUsers": 50,
      "unknownUsers": 0,
      "malePercentage": 58,
      "femalePercentage": 42
    },
    "totalMatchSuccessCount": 50,
    "topRequestedDepartments": [
      {
        "rank": 1,
        "departmentId": 1,
        "deptName": "항공운항학과",
        "collegeName": "항공학부",
        "status": "ACTIVE",
        "requestCount": 23
      },
      {
        "rank": 2,
        "departmentId": 2,
        "deptName": "간호학과",
        "collegeName": "보건학부",
        "status": "ACTIVE",
        "requestCount": 15
      }
    ],
    "generatedAt": "2026-09-11T15:00:00"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4.2 메인 노출 필드 기준

| 필드 | 화면 의미 | 서버 집계 기준 |
|---|---|---|
| `totalRegisteredUsers` | 총 가입자 수 | 온보딩 미완료·정지·제한 계정 포함, 탈퇴 계정만 제외 |
| `totalMatchSuccessCount` | 매칭 수 | `ACCEPTED` 상태 1:1 + `ACTIVE/ENDED` 상태 최종 그룹매칭 |
| `onlineUserCount` | 실시간 접속자 수 | 현재 서버에 인증된 STOMP 연결을 유지 중인 고유 사용자 수 |

- 한 사용자가 여러 기기 또는 여러 화면에서 연결해도 한 명으로 집계한다.
- 앱이 STOMP 연결을 하지 않으면 접속자로 집계되지 않는다.
- 현재 집계는 실행 중인 애플리케이션 서버 인스턴스 기준이다.
- 메인 화면에 필요한 세 값은 위 필드를 사용한다. `dailyActiveUsers`, `genderRatio`는 부가 통계다.
- `topRequestedDepartments`라는 필드명과 달리 모든 DB 학과를 반환한다. 실적 0회 학과도 포함한다.
- 학과 점수 `requestCount`는 해당 학과 사용자가 보낸 1:1 요청 수 + 받은 1:1 요청 수다.
- 점수 내림차순이며 동점은 공동 순위와 건너뛰기 방식이다. 예: `1, 2, 2, 4`.

### 4.3 실시간 접속자 초기값 조회

```http
GET /api/v1/statistics/online
```

```json
{
  "success": true,
  "data": {
    "onlineUserCount": 17,
    "updatedAt": "2026-09-11T15:00:01"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4.4 실시간 접속자 STOMP 구독

```text
/sub/statistics/online
```

이 구독은 STOMP 인증이 필요하다.

```json
{
  "onlineUserCount": 18,
  "updatedAt": "2026-09-11T15:00:02"
}
```

권장 메인 화면 처리:

1. `GET /api/v1/statistics/main`으로 화면 전체 초기값을 표시한다.
2. 전역 STOMP 연결을 만들고 `/sub/statistics/online`을 구독한다.
3. 구독 직후 `GET /api/v1/statistics/online`을 한 번 호출해 최신 초기값을 맞춘다.
4. 이후 수신 이벤트의 `onlineUserCount`로 숫자를 교체한다.
5. 재연결하면 재구독 후 `/statistics/online`을 다시 호출한다.

STOMP 연결 시점의 증가 이벤트는 구독 이전에 발생할 수 있으므로 3번 초기 조회를 생략하면 안 된다.

---

## 5. 마이페이지 추천인

### 5.1 기능 규칙

- 추천인 기능은 온보딩이 아니라 마이페이지에 배치한다.
- 추천 코드는 영문 대문자와 숫자를 모두 포함하는 6자리다. 예: `A7B2C9`.
- 소문자로 입력해도 서버가 대문자로 정규화한다.
- 요청에는 앞뒤 공백 없이 6자리만 전송한다.
- 친구가 내 코드를 처음 입력하면 친구와 나에게 티켓을 각각 5장 지급한다.
- 한 사용자는 다른 사람의 추천 코드를 한 번만 입력할 수 있다.
- 본인 코드 입력 및 두 사용자의 맞추천은 불가능하다.
- 내 코드를 사용할 수 있는 친구 수와 코드 유효기간에는 제한이 없다.
- 추천인과 코드 입력자 모두 `ACTIVE` 및 온보딩 `FULL` 상태여야 한다.

### 5.2 내 추천 정보 조회

```http
GET /api/v1/referrals/me
Authorization: Bearer {accessToken}
```

```json
{
  "success": true,
  "data": {
    "referralCode": "A7B2C9",
    "rewardTickets": 5,
    "referredFriendCount": 3,
    "hasEnteredReferralCode": false,
    "canEnterReferralCode": true
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 필드 | 설명 |
|---|---|
| `referralCode` | 내 고유 추천 코드 |
| `rewardTickets` | 한 번의 추천 성공 시 양쪽 지급량 |
| `referredFriendCount` | 내 코드를 사용한 친구 누적 수 |
| `hasEnteredReferralCode` | 내가 이미 다른 추천 코드를 입력했는지 여부 |
| `canEnterReferralCode` | 현재 입력창을 활성화할 수 있는지 여부 |

기존 사용자에게 코드가 없으면 첫 조회 시 생성되며 이후 동일한 코드가 유지된다.

### 5.3 추천 코드 입력

```http
POST /api/v1/referrals/redeem
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "referralCode": "A7B2C9"
}
```

처음 적용된 응답:

```json
{
  "success": true,
  "data": {
    "referralCode": "A7B2C9",
    "rewardTickets": 5,
    "myTickets": 15,
    "firstApplied": true,
    "redeemedAt": "2026-09-11T14:30:00"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

같은 사용자가 같은 코드를 재전송하면 중복 지급 없이 200을 반환하고 `firstApplied=false`가 된다. 이전과 다른 코드를 보내면 `REFERRAL-003` 오류다.

```json
{
  "success": true,
  "data": {
    "referralCode": "A7B2C9",
    "rewardTickets": 5,
    "myTickets": 15,
    "firstApplied": false,
    "redeemedAt": "2026-09-11T14:30:00"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

성공 시 앱의 티켓 잔액을 `myTickets`로 즉시 교체한다.

### 5.4 추천인 오류

| HTTP | 코드 | 앱 표시 권장 문구 |
|---|---|---|
| 400 | `COMMON-001` | 추천인 코드 형식을 확인해 주세요. |
| 400 | `REFERRAL-001` | 존재하지 않는 추천인 코드예요. |
| 400 | `REFERRAL-002` | 내 추천인 코드는 입력할 수 없어요. |
| 409 | `REFERRAL-003` | 이미 추천인 코드를 입력했어요. |
| 409 | `REFERRAL-004` | 서로의 추천인 코드를 입력할 수 없어요. |
| 403 | `REFERRAL-005` | 현재 계정에서는 추천인 기능을 사용할 수 없어요. |

### 5.5 마이페이지 구현 흐름

1. 추천인 메뉴 진입 시 `GET /referrals/me`를 호출한다.
2. 내 코드와 “친구와 나 모두 티켓 5장” 문구를 표시한다.
3. 코드 복사 및 공유 기능을 제공한다.
4. `canEnterReferralCode=true`일 때만 입력창과 등록 버튼을 표시한다.
5. 등록 중 버튼 중복 탭을 막는다.
6. 성공 시 `myTickets`를 반영하고 입력창을 숨긴다.
7. `firstApplied=true`일 때만 신규 지급 완료 안내를 표시한다.

---

## 6. 축제 쿠폰 500개 등록 기능

### 6.1 기능 규칙

- 서버에는 서로 다른 숫자 6자리 쿠폰 코드 500개가 미리 등록되어 있다.
- 쿠폰 한 개는 전체 사용자 중 한 명만 한 번 사용할 수 있다.
- 쿠폰 한 개 사용 시 티켓 5장을 지급한다.
- 한 사용자가 서로 다른 여러 쿠폰을 사용하는 것은 현재 서버 정책상 허용된다.
- 이미 사용된 같은 쿠폰을 재전송하면 성공 응답이 아니라 409 오류가 반환된다.
- 쿠폰 코드는 앞자리 `0`이 있을 수 있으므로 반드시 문자열로 보관한다.
- 실제 500개 쿠폰 코드 목록은 앱 리소스나 모바일 명세에 포함하지 않는다. 서버 배포 리소스와 오프라인 배포 자료에서만 관리한다.

### 6.2 쿠폰 입력

```http
POST /api/v1/tickets/coupons/redeem
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "code": "049893"
}
```

입력값은 숫자 6자리 문자열이어야 한다.

### 6.3 성공 응답

```json
{
  "success": true,
  "data": {
    "grantedTickets": 5,
    "beforeTickets": 10,
    "afterTickets": 15,
    "redeemedAt": "2026-09-11T16:00:00"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 필드 | 설명 |
|---|---|
| `grantedTickets` | 이번에 지급된 티켓 수 |
| `beforeTickets` | 지급 전 보유 티켓 |
| `afterTickets` | 지급 후 보유 티켓 |
| `redeemedAt` | 사용 완료 시각 |

성공 시 앱의 티켓 잔액을 `afterTickets`로 즉시 교체한다.

### 6.4 쿠폰 오류

| HTTP | 코드 | 앱 표시 권장 문구 | 조건 |
|---|---|---|---|
| 400 | `COMMON-001` | 숫자 6자리 쿠폰 코드를 입력해 주세요. | 요청 형식 검증 실패 |
| 400 | `COUPON-001` | 유효하지 않은 쿠폰 코드예요. | 형식 오류 또는 등록되지 않은 코드 |
| 409 | `COUPON-002` | 이미 사용된 쿠폰이에요. | 다른 사용자 또는 본인이 이미 사용 |
| 403 | `AUTH-002` | 현재 계정에서는 쿠폰을 사용할 수 없어요. | 활성 계정이 아님 |

쿠폰 API는 같은 요청 재시도에 대해 멱등 성공을 반환하지 않는다. 네트워크 타임아웃 후 재시도에서 `COUPON-002`를 받았다면 앱은 “이미 사용된 쿠폰”으로 안내하고 `GET /api/v1/users/me`의 `data.tickets`로 잔액을 다시 동기화한다.

### 6.5 쿠폰 화면 구현 흐름

1. 숫자 키패드와 6자리 입력칸을 제공한다.
2. 6자리가 아니면 등록 버튼을 비활성화한다.
3. 등록 요청 중 중복 탭을 막는다.
4. 성공 시 `afterTickets`를 반영하고 지급 완료 문구를 표시한다.
5. `COUPON-001`, `COUPON-002`를 구분해 안내한다.

---

## 7. Android/iOS 최종 체크리스트

### 1:1 매칭

- [ ] 추천 2종과 요청 전송에 `Idempotency-Key`를 추가한다.
- [ ] 같은 동작 재시도에는 같은 키, 새 동작에는 새 키를 사용한다.
- [ ] 상대 모델에서 `socialId`, `studentNum`, 계정 상태, 보유 티켓 의존 코드를 제거한다.
- [ ] nullable `admissionYear`를 추가하고 `2024`를 `24학번`으로 표시한다.
- [ ] 보낸 요청 취소 API를 연결한다.
- [ ] 요청 상태에 `CANCELLED`, `EXPIRED`를 추가한다.
- [ ] HTTP 200이어도 `status`를 확인하며 `ACCEPTED`와 유효한 `chatRoomId`일 때만 채팅으로 이동한다.
- [ ] 요청 전송 성공 후 `userTicketsRemaining`으로 티켓 잔액을 갱신한다.

### 그룹매칭

- [ ] 생성 화면에 2:2와 3:3만 남긴다.
- [ ] 공개 설정, 방 목록, 팀 이름, 상대 성별, 준비 완료, 임시 채팅을 제거한다.
- [ ] 초대 코드를 숫자가 아닌 문자열로 저장한다.
- [ ] 앱 복귀·재연결 시 `/me/state`로 상태를 복구한다.
- [ ] 서버의 `canStartMatching`, `canStopMatching`, `canLeave`, `canDisband`로 버튼을 제어한다.
- [ ] 팀별 STOMP 목적지를 구독하고 `QUEUE_UPDATED`로 순번을 즉시 갱신한다.
- [ ] `MATCHED`의 `finalChatRoomId`로 최종 채팅에 이동한다.

### 메인 통계

- [ ] `/statistics/main`의 가입자 수, 매칭 수, 접속자 수를 표시한다.
- [ ] 전역 STOMP 연결에서 `/sub/statistics/online`을 구독한다.
- [ ] 구독 직후 `/statistics/online`을 호출해 초기 접속자 수를 맞춘다.
- [ ] 재연결 시 재구독하고 REST 초기값을 다시 조회한다.

### 추천인

- [ ] 추천인 메뉴를 마이페이지에 배치한다.
- [ ] 추천 코드를 대문자+숫자 6자리 문자열로 처리한다.
- [ ] `canEnterReferralCode`로 입력 UI를 제어한다.
- [ ] 성공 시 `myTickets`를 앱 전역 티켓 잔액에 반영한다.
- [ ] `firstApplied=false`이면 중복 지급 완료 팝업을 띄우지 않는다.

### 쿠폰

- [ ] 쿠폰 코드를 숫자 6자리 문자열로 처리한다.
- [ ] 실제 500개 코드 목록을 앱 리소스에 포함하지 않는다.
- [ ] 성공 시 `afterTickets`를 앱 전역 티켓 잔액에 반영한다.
- [ ] 미등록 코드와 이미 사용된 코드를 구분해 안내한다.
