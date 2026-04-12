# AirConnect 변경 요약 및 API 반영

- 작성일: 2026-04-12
- 범위: "과팅 메인화면 모집중 개수 표시 수정" 이후 반영된 변경사항만 정리
- 대상 영역: 과팅, 채팅, 알림

## 1. 변경 요약

### 1.1 과팅 메인화면 모집중 개수 집계 기준 수정

- 메인화면의 `recruitableTeamRoomCount` 는 이제 실제 과팅 리스트와 동일한 기준으로 계산된다.
- 포함 조건:
  - `OPEN` 상태
  - `PUBLIC` 방
  - 정원이 아직 차지 않은 방
  - 현재 사용자와 같은 `teamGender` 의 방
- 제외 조건:
  - `QUEUE_WAITING`
  - `MATCHED`
  - `CLOSED`
  - `PRIVATE`
  - 현재 사용자 기준 이성 팀 방

의도:
- 메인 상단 숫자와 실제 리스트 개수가 다르게 보이던 문제를 해결

### 1.2 과팅 공개 리스트에서 이성 팀 노출 제거

- 공개 과팅 리스트는 현재 사용자와 같은 `teamGender` 의 팀만 내려간다.
- 예:
  - 남성 사용자는 남성 팀 모집글만 조회
  - 여성 사용자는 여성 팀 모집글만 조회

의도:
- UI 에서 이성 팀이 보이지만 실제 참가 대상이 아닌 혼선을 제거

### 1.3 알림 시간 표시 오차 수정

- 알림 목록의 `createdAt`, `readAt` 응답을 UTC offset 포함 형태로 일관되게 반환하도록 수정
- 서버 내부 처리도 UTC 기준으로 맞췄다.

의도:
- "30분 전 알림이 9시간 전으로 보이는 문제" 해결

### 1.4 과팅 임시방 추방 기능 추가

- 방장은 임시방에서 일반 팀원을 추방할 수 있다.
- 추방 시:
  - 대상 멤버는 임시방/임시채팅방에서 제거
  - 준비 상태가 초기화될 수 있음
  - 팀원 수가 감소함

의도:
- 잘못 들어온 인원이나 방 운영이 어려운 인원을 리더가 정리할 수 있게 함

### 1.5 과팅 임시방 공개/비공개 전환 기능 추가

- 방장은 임시방의 `visibility` 를 `PUBLIC` / `PRIVATE` 로 변경할 수 있다.
- `PRIVATE` 로 전환할 때 초대코드가 없으면 자동 발급된다.

의도:
- 공개 모집에서 팀 구성이 어느 정도 된 뒤 비공개로 전환하는 운영 흐름 지원

### 1.6 채팅방 상대 프로필 응답에 성별 추가

- 채팅 상대 프로필 조회 응답에 최상위 `gender` 필드를 추가했다.
- 과팅 임시방 멤버 프로필 응답과 1:1 채팅 상대 프로필 응답 모두 동일한 DTO 를 사용한다.

의도:
- 클라이언트에서 성별별 UI 분기 가능

### 1.7 과팅 매칭 즉시 이동 제거, 10초 지연 확정으로 변경

- 기존:
  - 매칭이 잡히면 즉시 최종 그룹 채팅방 생성
  - 즉시 이동/알림 발생
- 변경:
  - 매칭이 잡히면 우선 임시방 상태가 `MATCHED` 로 변경
  - 최종 그룹 채팅방 생성은 약 10초 뒤 워커가 처리
  - 그 시점에 최종방 ID, 푸시/인앱 알림, 최종 이동 이벤트가 내려감

의도:
- 사용자가 "매칭되었습니다" 상태를 인지할 시간을 확보

중요:
- 클라이언트는 임시방 상태가 `MATCHED` 라고 해서 바로 최종방으로 이동하면 안 된다.
- 반드시 아래 조건 중 하나를 보고 이동해야 한다.
  - `MATCHED` 실시간 이벤트 수신
  - `finalGroupRoomId`, `finalChatRoomId` 존재
  - `GET /api/v1/matching/team-rooms/{teamRoomId}/final-room` 조회 성공

## 2. API 변경 영향

## 2.1 스키마 변경이 없는 API

### `GET /api/v1/matching/team-rooms/public`
### `GET /api/v1/matching/team-rooms/recruitable`

- 응답 DTO 구조는 동일
- 조회 의미만 변경

현재 기준:
- `OPEN`
- `PUBLIC`
- 정원 미달
- 현재 사용자와 같은 `teamGender`

### `GET /api/v1/matching/team-rooms/me/state`

- 응답 DTO 구조는 동일
- `recruitableTeamRoomCount` 의 집계 기준이 실제 공개 모집 리스트와 동일하게 변경

현재 의미:
- 현재 사용자 기준으로 메인화면에 보여야 하는 모집중 과팅 수

### `GET /api/v1/notifications`
### `PATCH /api/v1/notifications/{notificationId}/read`

- 응답 구조는 동일
- `createdAt`, `readAt` 는 UTC offset 포함 문자열로 해석해야 함

예시:

```json
{
  "createdAt": "2026-04-12T03:20:00.000000+00:00",
  "readAt": "2026-04-12T03:50:00.000000+00:00"
}
```

클라이언트 처리 원칙:
- 로컬 타임존으로 변환해서 "n분 전" 계산
- 문자열을 로컬 시각으로 직접 가정하면 안 됨

## 2.2 새로 추가된 API

### 1) 임시방 멤버 추방

- Method: `DELETE`
- Path: `/api/v1/matching/team-rooms/{teamRoomId}/members/{targetUserId}`
- 설명: 임시방 리더가 일반 멤버를 추방

권한:
- 리더만 가능
- 자기 자신 추방 불가
- 이미 나간 멤버 / 리더 멤버는 추방 불가

응답:
- `TemporaryTeamRoomResponse`

예시:

```http
DELETE /api/v1/matching/team-rooms/101/members/22
Authorization: Bearer {accessToken}
```

```json
{
  "id": 101,
  "status": "OPEN",
  "currentMemberCount": 1,
  "members": [
    {
      "userId": 11,
      "leader": true,
      "active": true
    },
    {
      "userId": 22,
      "leader": false,
      "active": false
    }
  ]
}
```

### 2) 임시방 공개/비공개 전환

- Method: `PATCH`
- Path: `/api/v1/matching/team-rooms/{teamRoomId}/visibility`
- 설명: 임시방 리더가 공개 여부 변경

Request:

```json
{
  "visibility": "PRIVATE"
}
```

Response:
- `TemporaryTeamRoomResponse`

특이사항:
- `PRIVATE` 로 전환 시 초대코드가 없으면 자동 발급

예시:

```http
PATCH /api/v1/matching/team-rooms/101/visibility
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "visibility": "PRIVATE",
  "inviteCode": "A1B2C3"
}
```

## 2.3 응답 필드가 추가된 API

### 1) 채팅 상대 프로필 조회

- Method: `GET`
- Path: `/api/v1/chat/rooms/{roomId}/counterpart-profile`

### 2) 채팅방 특정 참여자 프로필 조회

- Method: `GET`
- Path: `/api/v1/chat/rooms/{roomId}/participants/{targetUserId}/profile`

### 3) 과팅 임시방 멤버 프로필 조회

- Method: `GET`
- Path: `/api/v1/matching/team-rooms/{teamRoomId}/members/{targetUserId}/profile`

추가 필드:

```json
{
  "userId": 22,
  "nickname": "상대방",
  "gender": "FEMALE",
  "profile": {
    "gender": "FEMALE"
  }
}
```

변경 포인트:
- 기존에는 클라이언트가 `profile.gender` 를 보거나, 경우에 따라 성별이 비어 있는 응답을 따로 해석해야 했다.
- 이제 최상위 `gender` 를 바로 사용하면 된다.

권장 처리:
- UI 에서 우선 `gender` 사용
- 레거시 대응이 필요하면 `profile.gender` 는 fallback 으로만 사용

## 2.4 동작 의미가 바뀐 API

### `POST /api/v1/matching/team-rooms/{teamRoomId}/queue/start`

기존:
- 매칭이 성사되면 응답에 최종방 ID 가 바로 채워질 수 있었음

현재:
- 매칭이 바로 성사되어도 우선 `MATCHED` 상태만 내려갈 수 있음
- 이 시점에는 `finalGroupRoomId`, `finalChatRoomId` 가 `null`
- 약 10초 후 워커가 최종방 생성 완료 후 실시간 이벤트/재조회로 최종방 정보를 받게 됨

예시 1. 즉시 매칭 예약만 된 응답

```json
{
  "teamRoomId": 101,
  "status": "MATCHED",
  "position": 0,
  "aheadCount": 0,
  "totalWaitingTeams": 0,
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}
```

예시 2. 10초 후 최종방 생성 완료 상태

```json
{
  "teamRoomId": 101,
  "status": "CLOSED",
  "position": 0,
  "aheadCount": 0,
  "totalWaitingTeams": 0,
  "finalGroupRoomId": 701,
  "finalChatRoomId": 9901,
  "matched": true
}
```

클라이언트 구현 규칙:
- `status == MATCHED` 만으로 화면 전환 금지
- 아래 조건에서만 최종방 이동
  - `matched == true`
  - `finalGroupRoomId != null && finalChatRoomId != null`
  - `MATCHED` 실시간 이벤트 수신
  - `GET /api/v1/matching/team-rooms/{teamRoomId}/final-room` 조회 성공

## 2.5 실시간 이벤트 처리 규칙

구독 채널:
- `/sub/matching/team-room/{teamRoomId}`

이제 과팅 매칭 흐름은 2단계로 본다.

### 단계 1. 매칭 예약

예시:

```json
{
  "eventType": "QUEUE_UPDATED",
  "teamRoomId": 101,
  "status": "MATCHED",
  "finalGroupRoomId": null,
  "finalChatRoomId": null,
  "matched": false
}
```

의미:
- 상대 팀이 잡혔음
- 아직 최종방 생성 전
- UI 는 "매칭되었습니다" 또는 카운트다운/대기 상태를 보여줄 수 있음

### 단계 2. 최종방 생성 완료

예시:

```json
{
  "eventType": "MATCHED",
  "teamRoomId": 101,
  "status": "CLOSED",
  "finalGroupRoomId": 701,
  "finalChatRoomId": 9901,
  "matched": true
}
```

의미:
- 최종방 생성 완료
- 이 시점에만 최종 그룹 채팅방으로 이동

## 3. 서버 구현 메모

- 지연 확정은 워커가 처리하므로 운영 환경에서 아래 설정이 꺼져 있으면 안 된다.

```properties
matching.queue.worker.enabled=true
```

- 워커가 꺼져 있으면:
  - 임시방은 `MATCHED` 상태로 남을 수 있음
  - 최종 그룹 채팅방 생성이 완료되지 않음

## 4. 문서 적용 대상

이 문서는 아래 기존 문서의 변경 보충본이다.

- `docs/groupmatching-mobile-api-spec.md`
- `docs/chat-api.final.md`
- 알림 API 소비 코드

기존 문서와 충돌할 경우, 본 문서의 변경 항목을 우선 적용한다.
