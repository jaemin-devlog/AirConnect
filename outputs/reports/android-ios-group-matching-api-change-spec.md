# Android/iOS 그룹매칭 API 변경 명세

대상: Android / iOS  
기준 백엔드: `develop` 통합 예정 버전  
범위: 이미 전달한 `1-1매칭 api 명세 변경내역.md`에 포함되지 않은 추가 앱 작업

## 결론

- 이미 전달한 1:1 명세 외에 1:1 API에서 추가로 바꿀 내용은 없다.
- 그룹매칭은 기존 API와 호환되지 않는 단순화가 적용되므로 Android/iOS 모두 아래 내용을 반영해야 한다.
- 서버를 먼저 배포하면 기존 그룹매칭 화면의 일부 요청이 404 또는 405로 실패할 수 있다. 앱 반영 일정과 서버 배포 일정을 맞춘다.

## 1. 최종 사용자 흐름

1. 사용자가 `2:2` 또는 `3:3`을 선택해 팀을 만든다.
2. 생성된 6자리 초대 코드를 같은 성별의 친구에게 공유한다.
3. 친구는 초대 코드를 입력하거나 초대 링크를 열어 팀에 들어온다.
4. 정원이 차고 전원의 티켓이 충분하면 방장만 매칭을 시작한다.
5. 모든 팀원은 대기 순서를 확인할 수 있고, 누구나 매칭 대기를 중지할 수 있다.
6. 상대 성별 팀과 매칭되면 최종 그룹 채팅방으로 이동한다.

다음 UI와 기능은 제거한다.

- 공개방·비공개방 선택
- 방 목록과 모르는 팀 참가
- 팀 이름 입력
- 상대 성별 선택
- 준비 완료
- 매칭 전 임시 채팅

## 2. 기본 계약

기본 경로:

```text
/api/v1/matching/team-rooms
```

- 모든 요청에 기존 로그인 인증을 사용한다.
- 그룹매칭 API에는 `Idempotency-Key`가 필요하지 않다.
- 성공 응답은 `ApiResponse.data`로 감싸지지 않고 아래 DTO가 JSON 최상위에 바로 반환된다.
- 팀 인원 enum은 `TWO`, `THREE`만 사용한다.
- 팀 성별 enum은 `M`, `F`이며 생성자 프로필을 기준으로 서버가 결정한다.
- 초대 코드는 6자리 숫자 **문자열**이다. `012345`처럼 앞의 `0`을 보존한다.

## 3. 사용 API

| 기능 | 메서드 | 경로 | 요청 본문 | 성공 |
| --- | --- | --- | --- | --- |
| 팀 생성 | POST | `/api/v1/matching/team-rooms` | `{"teamSize":"TWO"}` | 201 팀 응답 |
| 초대 코드 입장 | POST | `/api/v1/matching/team-rooms/join-by-invite` | `{"inviteCode":"012345"}` | 200 팀 응답 |
| 내 전체 상태 복구 | GET | `/api/v1/matching/team-rooms/me/state` | 없음 | 200 상태 응답 |
| 내 활성 팀 조회 | GET | `/api/v1/matching/team-rooms/me` | 없음 | 200 팀 / 없으면 204 |
| 초대 코드 재발급 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/invite-code` | 없음 | 200 팀 응답 |
| 매칭 시작 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/queue/start` | 없음 | 200 대기 응답 |
| 대기 조회 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/queue` | 없음 | 200 대기 응답 |
| 매칭 중지 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/queue/leave` | 없음 | 200 팀 응답 |
| 팀 나가기 | POST | `/api/v1/matching/team-rooms/{teamRoomId}/leave` | 없음 | 204 |
| 팀 해산 | DELETE | `/api/v1/matching/team-rooms/{teamRoomId}` | 없음 | 204 |
| 팀원 내보내기 | DELETE | `/api/v1/matching/team-rooms/{teamRoomId}/members/{userId}` | 없음 | 200 팀 응답 |
| 팀원 프로필 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/members/{userId}/profile` | 없음 | 200 기존 프로필 응답 |
| 최종 그룹방 조회 | GET | `/api/v1/matching/team-rooms/{teamRoomId}/final-room` | 없음 | 200 최종방 / 없으면 204 |

팀 생성 요청이 타임아웃되면 바로 다시 생성하지 말고 먼저 `GET /me/state`로 생성 여부를 확인한다.

## 4. 팀 응답 모델

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
      "nickname": "방장",
      "profileImage": null,
      "leader": true,
      "joinedAt": "2026-09-11T13:00:00",
      "hasEnoughTickets": true
    }
  ]
}
```

앱은 버튼 권한을 직접 추측하지 말고 다음 서버 필드로 제어한다.

- `canStartMatching`: 매칭 시작 버튼
- `canStopMatching`: 매칭 중지 버튼
- `canLeave`: 팀 나가기 버튼
- `canDisband`: 팀 해산 버튼

`requiredTickets`는 사용자 한 명에게 필요한 티켓 수다. 2:2는 2장, 3:3은 3장이다. 다른 사용자의 실제 티켓 수량은 제공하지 않고 `hasEnoughTickets`만 제공한다.

삭제된 응답 필드:

- `teamName`
- `visibility`
- `opponentGenderFilter`
- `tempChatRoomId`
- `readyMemberCount`
- `allMembersReady`
- `queueToken`
- `members[].ready`

## 5. 앱 상태 복구

앱 실행, 화면 재진입, WebSocket 재연결 시 아래 API를 호출한다.

```http
GET /api/v1/matching/team-rooms/me/state
```

응답 최상위 필드:

```text
state, teamRoom, queueSnapshot, finalRoom, matchingSubscriptionDestination
```

| `state` | 앱 화면 |
| --- | --- |
| `IDLE` | 2:2·3:3 생성 및 초대 코드 입력 화면 |
| `TEMPORARY_TEAM_ROOM` | `teamRoom.status`에 맞는 팀 구성 또는 대기 화면 |
| `FINAL_GROUP_CHAT_ROOM` | `finalRoom.chatRoomId`의 최종 채팅방 |

`teamRoom.status` 처리:

| 상태 | 앱 처리 |
| --- | --- |
| `OPEN` | 팀원·초대 코드와 서버 권한에 맞는 버튼 표시 |
| `READY_CHECK` | 구버전 데이터 호환 상태이므로 `OPEN`과 동일하게 처리 |
| `QUEUE_WAITING` | 대기 순서와 매칭 중지 버튼 표시 |
| `MATCHED` | 최종 채팅 생성 중 화면, 중지·나가기·해산 숨김 |
| `CLOSED`, `CANCELLED` | 현재 화면을 닫고 `/me/state` 재조회 |

## 6. 대기 순서

대기 응답:

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
- `aheadCount`는 내 팀 앞의 팀 수다.
- `totalWaitingTeams`는 같은 인원·같은 성별 조건에서 대기 중인 팀 수다.
- 예상 대기 시간이나 상대 성별 팀 수가 아니다.
- `matched == true`이고 `finalChatRoomId != null`이면 최종 채팅방으로 이동한다.
- 대기 화면에서는 약 5초 간격으로 대기 조회를 병행하고 화면 종료 시 폴링을 중단한다.

## 7. WebSocket 이벤트

`/me/state`의 `matchingSubscriptionDestination` 또는 아래 경로를 구독한다.

```text
/sub/matching/team-room/{teamRoomId}
```

이벤트 모델:

```json
{
  "eventType": "QUEUE_UPDATED",
  "teamRoomId": 101,
  "status": "QUEUE_WAITING",
  "position": 3,
  "aheadCount": 2,
  "totalWaitingTeams": 5,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false,
  "occurredAt": "2026-09-11T13:02:00"
}
```

| `eventType` | 앱 처리 |
| --- | --- |
| `STATUS_CHANGED` | `/me/state` 재조회 |
| `QUEUE_UPDATED` | 대기 순서 갱신 |
| `MATCHED` | `finalChatRoomId`가 있으면 최종 채팅방으로 이동 |

WebSocket 이벤트는 화면 갱신 신호로 사용한다. 앱 복귀·재연결 시에는 이벤트 수신 여부와 관계없이 `/me/state`를 조회한다.

## 8. Push 알림 변경

추가되는 알림 타입:

```text
TEAM_MATCHING_STOPPED
```

이 알림은 다른 팀원이 매칭 대기를 중지했을 때 수신한다.

```json
{
  "notificationType": "TEAM_MATCHING_STOPPED",
  "deeplink": "airconnect://matching/team-rooms/101",
  "payload": {
    "teamRoomId": 101,
    "stoppedByUserId": 20,
    "status": "OPEN"
  }
}
```

그룹매칭 관련 유지 타입:

- `TEAM_MEMBER_JOINED`
- `TEAM_MEMBER_LEFT`
- `TEAM_ROOM_CANCELLED`
- `GROUP_MATCHED`

더 이상 신규 발송하지 않는 타입:

- `TEAM_READY_REQUIRED`
- `TEAM_MEMBER_READY_CHANGED`
- `TEAM_ALL_READY`

딥링크 처리:

- 팀 관련: `airconnect://matching/team-rooms/{teamRoomId}`
- 팀 해산: `airconnect://matching/team-rooms`
- 초대: `airconnect://matching/join?inviteCode={inviteCode}`
- 최종 그룹 채팅: `airconnect://group-chat/final/{finalGroupRoomId}`

팀 관련 딥링크를 열면 `/me/state`를 조회해 실제 유효한 화면을 결정한다.

## 9. 제거된 API

아래 API 호출과 관련 화면을 앱에서 제거한다.

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

## 10. Android/iOS 체크리스트

- [ ] 그룹매칭 생성 화면을 `TWO`, `THREE` 선택만 남긴다.
- [ ] 방 목록·공개 설정·팀 이름·상대 성별·준비 완료·임시 채팅을 제거한다.
- [ ] 초대 코드를 문자열로 저장하고 앞자리 `0`을 유지한다.
- [ ] 초대 링크를 열면 코드를 추출해 `join-by-invite`를 호출한다.
- [ ] 팀 응답의 `can*` 필드로 버튼을 제어한다.
- [ ] 앱 실행·화면 재진입·소켓 재연결 시 `/me/state`를 호출한다.
- [ ] 대기 화면에서 순서 조회와 WebSocket 구독을 함께 사용한다.
- [ ] `TEAM_MATCHING_STOPPED` 알림 타입을 추가한다.
- [ ] 제거된 응답 필드와 API 의존 코드를 삭제한다.
- [ ] `matched == true`이고 `finalChatRoomId`가 있을 때만 최종 채팅으로 이동한다.

