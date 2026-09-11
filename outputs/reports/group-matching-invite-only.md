# 그룹매칭: 친구 초대 전용 API 명세 및 배포 전환

## 적용 범위

백엔드 기능 구현 기준이다. Android/iOS 화면, 초대 링크 처리 및 서버 배포는 별도로 반영한다.
기존 API와 호환되지 않는 변경이 포함되므로 앱 전환과 서버 배포 일정을 맞춘다.

사용 흐름: 인원 선택 → 팀 생성 → 코드로 친구 초대 → 방장이 매칭 시작 → 대기 순서 → 최종 그룹 채팅.

- 생성 입력은 `teamSize` 하나다. `TWO`는 2:2, `THREE`는 3:3이다.
- 팀 성별은 생성자의 프로필로 결정되며 같은 성별의 친구만 입장한다.
- 상대는 동일한 인원수의 반대 성별 팀이다.
- 방 이름·공개 여부·상대 성별 선택·방 목록·준비 완료·매칭 전 채팅을 제공하지 않는다.
- 팀 구성원은 초대 코드를 공유할 수 있다. 코드 재발급은 방장만 가능하다.
- 정원이 차고 전원 티켓이 충분하면 방장만 매칭을 시작할 수 있다.
- 모든 활성 팀원이 매칭 대기를 중지할 수 있다. 팀은 유지되며 방장만 다시 시작한다.
- 대기 중 나가기/추방은 차단된다. 대기 중지 후 나간다. 방장은 나가기 대신 해산한다.
- 상대 팀과 매칭된 `MATCHED`부터 중지/나가기/해산할 수 없다.
- 2:2는 사용자당 2장, 3:3은 사용자당 3장이다. 시작 시 잔액을 확인하고 최종 채팅 생성 시 차감한다.

## 요청

기본 경로: `/api/v1/matching/team-rooms`
모든 요청은 로그인 인증이 필요하다. 성공 응답은 아래 DTO 자체이며 `data`로 감싸지 않는다.
오류는 기존 공통 오류 응답을 사용한다.

| 동작 | 메서드·경로 | 본문 | 성공 응답 |
|---|---|---|---|
| 생성 | POST 기본 경로 | `{"teamSize":"TWO"}` | 201 팀 |
| 코드 입장 | POST /join-by-invite | `{"inviteCode":"012345"}` | 200 팀 |
| 내 상태 복구 | GET /me/state | 없음 | 200 상태 |
| 내 팀 조회 | GET /me | 없음 | 200 팀 / 없으면 204 |
| 코드 재발급 | POST /{id}/invite-code | 없음 | 200 팀 |
| 매칭 시작 | POST /{id}/queue/start | 없음 | 200 대기 상태 |
| 대기 조회 | GET /{id}/queue | 없음 | 200 대기 상태 |
| 매칭 중지 | POST /{id}/queue/leave | 없음 | 200 팀 |
| 팀 나가기 | POST /{id}/leave | 없음 | 204 |
| 팀 해산 | DELETE /{id} | 없음 | 204 |
| 친구 추방 | DELETE /{id}/members/{userId} | 없음 | 200 팀 |
| 팀원 프로필 | GET /{id}/members/{userId}/profile | 없음 | 기존 프로필 DTO |
| 최종 그룹방 | GET /{id}/final-room | 없음 | 200 최종방 / 없으면 204 |

초대 코드는 6자리 숫자 **문자열**이다. 앞자리 0을 유지한다.
같은 팀에 코드로 다시 입장하거나 이미 중지된 팀에서 중지를 재요청하면 상태를 반환한다.
이미 대기 중인 팀에서 방장이 시작을 재요청하면 기존 대기 상태를 반환한다.
네트워크 실패 후 생성 요청을 무조건 반복하지 말고 /me/state로 생성 여부부터 확인한다.

다음 경로는 제거되었으므로 호출하지 않는다:
`GET /public`, `GET /recruitable`, `POST /{id}/join`,
`PATCH /{id}/visibility`, `PATCH /{id}/ready`,
`GET/POST /{id}/chat/messages`, `PATCH /{id}/chat/read`.

## 팀 응답

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
  "createdAt": "2026-09-10T18:00:00",
  "updatedAt": "2026-09-10T18:01:00",
  "members": [
    {"userId":10,"nickname":"방장","profileImage":null,"leader":true,"joinedAt":"2026-09-10T18:00:00","hasEnoughTickets":true},
    {"userId":20,"nickname":"친구","profileImage":null,"leader":false,"joinedAt":"2026-09-10T18:01:00","hasEnoughTickets":true}
  ]
}
```

`members`에는 현재 참여 중인 친구만 포함한다. 다른 사람의 티켓 수량은 공개하지 않는다.
버튼은 `canStartMatching / canStopMatching / canLeave / canDisband`로 제어한다.
잔액·인원은 응답 이후 바뀔 수 있으므로 실제 요청에서 서버가 다시 검증한다.
`teamName / visibility / opponentGenderFilter / tempChatRoomId / readyMemberCount /
allMembersReady / queueToken / members.ready`는 삭제되었다.

초대 링크는 앱의 커스텀 링크다. 앱이 링크에서 코드를 추출하여 코드 입장 API를 호출해야 한다.
링크 클릭만으로 서버가 자동 입장시키거나 미설치 사용자에게 설치 후 입장을 복원하지는 않는다.

## 상태 복구와 화면 갱신

/me/state 응답은 `state, teamRoom, queueSnapshot, finalRoom, matchingSubscriptionDestination`을 포함한다.
기존 `recruitableTeamRoomCount`는 제거되었다.

| 상태 | 화면 처리 |
|---|---|
| IDLE | 2:2 / 3:3 만들기, 초대 코드 입력 |
| TEMPORARY_TEAM_ROOM + OPEN | 친구 목록·초대 코드·매칭 시작 버튼 |
| TEMPORARY_TEAM_ROOM + QUEUE_WAITING | 대기 순서·중지 버튼 |
| TEMPORARY_TEAM_ROOM + MATCHED | 최종 그룹 채팅 생성 중, 중지 버튼 숨김 |
| FINAL_GROUP_CHAT_ROOM | finalRoom.chatRoomId로 최종 채팅 이동 |

구버전 DB의 READY_CHECK는 앱에 OPEN으로 내려간다. 별도 준비 화면을 만들지 않는다.
최종 그룹방 응답 필드는 기존과 동일하다.

팀 생성/입장 후 또는 앱 복귀 시 /me/state를 조회한다.
`matchingSubscriptionDestination` 또는 `/sub/matching/team-room/{id}`를 구독한다.
`STATUS_CHANGED` 수신 시 /me/state를 다시 조회한다.
`QUEUE_UPDATED`로 대기 정보를 갱신하고, `MATCHED`의 finalChatRoomId로 채팅을 연다.
이벤트가 빠지거나 연결이 끊어질 수 있으므로 화면 재진입·소켓 재연결 시에도 상태를 조회한다.
대기 화면이 보일 때는 약 5초 간격으로 대기 조회를 병행하고 화면을 벗어나면 중단한다.
대기 순서 변경마다 모든 팀에 이벤트가 발송되는 것은 아니다.

대기 응답 예:
```json
{"teamRoomId":101,"status":"QUEUE_WAITING","position":3,"aheadCount":2,"totalWaitingTeams":5,"finalGroupRoomId":null,"finalChatRoomId":null,"matched":false}
```

`position`은 동일 인원·동일 성별 팀 중 1부터 시작하는 순번이다.
`aheadCount`는 앞의 팀 수, `totalWaitingTeams`는 자신을 포함한 해당 대기 팀 수다.
DB의 대기 시작 시각, 팀 ID 순서로 계산한다. 상대 성별 팀 수나 예상 시간은 아니다.
대기 외 상태에서는 순번을 표시하지 않는다. 음수라면 상태를 재조회한다.
매칭 시작 직후 이미 MATCHED 또는 CLOSED가 반환될 수 있으므로 항상 status/matched를 먼저 확인한다.

## 알림

- 팀 합류: 기존 TEAM_MEMBER_JOINED. 정원이 차면 방장이 시작 가능하다는 문구 포함.
- 대기 중지: 신규 TEAM_MATCHING_STOPPED. 중지한 사람을 제외한 팀원에게 전송.
  payload에 teamRoomId, stoppedByUserId, status가 포함된다.
- 퇴장/해산/최종 매칭: TEAM_MEMBER_LEFT, TEAM_ROOM_CANCELLED, GROUP_MATCHED 유지.
- TEAM_READY_REQUIRED, TEAM_MEMBER_READY_CHANGED, TEAM_ALL_READY는 신규 발송하지 않는다.
- 팀 관련 링크: airconnect://matching/team-rooms/{id}. 열면 /me/state로 유효한 현재 화면을 결정한다.

## 배포 전환

1. 앱에서 위 요청/응답과 신규 중지 알림 타입, 초대 링크를 반영한다.
2. 기존 서버를 중지한 상태에서 src/main/resources/sql/group_matching_invite_only_migration_mysql.sql을 실행한다.
3. 새 서버를 시작한다. 임시 채팅 ID 컬럼의 NULL 허용과 구버전 임시 채팅 숨김이 필수다.

기존 메시지와 준비 기록은 이력 보존을 위해 DB에 남는다. 새 팀은 임시 채팅이나 준비 기록을 만들지 않는다.
과거 team_name/visibility/opponent_gender_filter 컬럼은 이력·관리자 호환을 위해 내부 기본값으로만 사용한다.
신규 API에는 노출하지 않으며 사용자 변경 기능도 제공하지 않는다.
기존 데이터에 이미 구성된 팀은 유지한다. 누가 실제 친구인지는 서버가 판단하지 않으며 초대 코드 소지가 입장 기준이다.

기존 그룹매칭 테스트는 새 계약에 맞게 갱신했다. 초대 생성·동성 참여·이성 참여 차단·방장 시작·
전원 티켓 검사·모든 팀원의 대기 중지·대기 중 이탈 차단·대기 순번·해산 후 활성 방 제거·
매칭 후 임시 팀 종료와 최종 채팅 생성·동시 최종화 및 티켓 롤백을 검증한다.
전체 백엔드 테스트도 통과했다. 실제 Android/iOS 앱·운영 MySQL 마이그레이션·FCM 실기기 검증은 별도다.
