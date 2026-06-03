# Admin Frontend New Features Spec

이번 관리자 개선 작업으로 새로 추가된 프론트 구현 대상만 정리한 명세입니다.

## 공통

모든 API는 관리자 access token이 필요합니다.

```http
Authorization: Bearer {accessToken}
```

관리자 API 응답은 `ApiResponse<T>` 래퍼입니다.

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "trace-id"
}
```

---

## 1. 운영 요약 대시보드

### 화면 목적

서비스 운영 현황을 한 화면에서 요약합니다.

### API

```http
GET /api/v1/admin/operations/summary
```

### 주요 UI

- 운영 기간
- 누적 가입자
- 온보딩 완료자
- DAU / WAU / MAU
- 누적 매칭 성공
- 누적 채팅 메시지
- 미처리 신고
- Outbox backlog

### Response `data`

```json
{
  "operationStartedAt": "2026-05-01T12:00:00",
  "operationDays": 34,
  "totalRegisteredUsers": 120,
  "onboardingCompletedUsers": 92,
  "dailyActiveUsers": 15,
  "weeklyActiveUsers": 44,
  "monthlyActiveUsers": 80,
  "totalMatchSuccessCount": 18,
  "totalChatMessages": 320,
  "unresolvedReports": 3,
  "outboxBacklog": 5,
  "generatedAt": "2026-06-03T18:00:00"
}
```

---

## 2. Outbox 모니터링

### 화면 목적

비동기 푸시 발송 상태와 적체/실패를 운영자가 확인합니다.

### API

```http
GET /api/v1/admin/operations/outbox
```

### 주요 UI

- Outbox 상태별 카드: `PENDING`, `PROCESSING`, `SENT`, `FAILED`, `SKIPPED`
- 오래된 PENDING 수
- 오래 잡힌 PROCESSING 수
- 평균 발송 시간
- 최근 실패 목록 테이블

### Response `data`

```json
{
  "statusCounts": [
    { "status": "PENDING", "count": 3 },
    { "status": "PROCESSING", "count": 1 },
    { "status": "SENT", "count": 240 },
    { "status": "FAILED", "count": 2 },
    { "status": "SKIPPED", "count": 14 }
  ],
  "oldPendingCount": 0,
  "staleProcessingCount": 0,
  "averageDeliverySeconds": 0.8,
  "recentFailures": [
    {
      "outboxId": 101,
      "notificationId": 55,
      "userId": 7,
      "status": "FAILED",
      "attemptCount": 3,
      "lastErrorCode": "FCM_UNREGISTERED",
      "lastErrorMessage": "Token is not registered",
      "updatedAt": "2026-06-03T17:30:00"
    }
  ],
  "generatedAt": "2026-06-03T18:00:00"
}
```

### Screenshot Target

```text
outbox-monitoring.png
```

---

## 3. 매칭 퍼널

### 화면 목적

1:1 매칭 흐름에서 어디서 이탈이 발생하는지 보여줍니다.

### API

```http
GET /api/v1/admin/operations/matching-funnel?days=30
```

### Query

| name | type | required | default |
|---|---:|---:|---:|
| `days` | number | no | `30` |

### 주요 UI

- 조회 기간 선택: 7일 / 30일 / 90일
- 퍼널 차트
- 추천 조회 수
- 매칭 요청 수
- 수락 수
- 거절/만료 수
- 수락률
- 평균 응답 시간
- 채팅방 생성 수

### Response `data`

```json
{
  "days": 30,
  "since": "2026-05-04T18:00:00",
  "recommendationViewCount": 120,
  "requestCount": 32,
  "acceptedCount": 6,
  "rejectedOrExpiredCount": 8,
  "acceptanceRatePercentage": 19,
  "averageResponseSeconds": 420.5,
  "chatRoomCreatedCount": 6,
  "steps": [
    {
      "key": "recommendation_refreshed",
      "label": "추천 새로고침",
      "count": 120,
      "conversionFromPreviousPercentage": null
    },
    {
      "key": "request_sent",
      "label": "매칭 요청",
      "count": 32,
      "conversionFromPreviousPercentage": 27
    },
    {
      "key": "request_accepted",
      "label": "요청 수락",
      "count": 6,
      "conversionFromPreviousPercentage": 19
    },
    {
      "key": "request_rejected_or_expired",
      "label": "거절/만료",
      "count": 8,
      "conversionFromPreviousPercentage": 25
    },
    {
      "key": "chat_room_created",
      "label": "채팅방 생성",
      "count": 6,
      "conversionFromPreviousPercentage": 100
    }
  ],
  "generatedAt": "2026-06-03T18:00:00"
}
```

### Screenshot Target

```text
matching-funnel.png
```

---

## 4. 그룹 매칭 퍼널

### 화면 목적

그룹 매칭의 팀방 생성부터 최종 그룹 채팅방 생성까지의 흐름을 보여줍니다.

### API

```http
GET /api/v1/admin/operations/group-matching-funnel?days=30
```

### Query

| name | type | required | default |
|---|---:|---:|---:|
| `days` | number | no | `30` |

### 주요 UI

- 조회 기간 선택: 7일 / 30일 / 90일
- 그룹 매칭 퍼널 차트
- 팀방 생성 수
- 팀방 참여 수
- 준비 완료 팀 수
- 큐 진입 수
- 매칭 성공 수
- 최종 그룹 채팅방 생성 수
- 평균 큐 대기 시간

### Response `data`

```json
{
  "days": 30,
  "since": "2026-05-04T18:00:00",
  "teamRoomCreatedCount": 20,
  "teamRoomJoinCount": 48,
  "readyTeamCount": 12,
  "queueEnteredCount": 10,
  "matchSuccessCount": 4,
  "finalGroupChatRoomCreatedCount": 4,
  "averageQueueWaitSeconds": 95.2,
  "steps": [
    {
      "key": "team_room_created",
      "label": "팀방 생성",
      "count": 20,
      "conversionFromPreviousPercentage": null
    },
    {
      "key": "team_room_joined",
      "label": "팀방 참여",
      "count": 48,
      "conversionFromPreviousPercentage": 240
    },
    {
      "key": "ready_team",
      "label": "준비 완료 팀",
      "count": 12,
      "conversionFromPreviousPercentage": 60
    },
    {
      "key": "queue_entered",
      "label": "큐 진입",
      "count": 10,
      "conversionFromPreviousPercentage": 83
    },
    {
      "key": "match_success",
      "label": "매칭 성공",
      "count": 4,
      "conversionFromPreviousPercentage": 40
    },
    {
      "key": "final_group_chat_created",
      "label": "최종 그룹 채팅방 생성",
      "count": 4,
      "conversionFromPreviousPercentage": 100
    }
  ],
  "generatedAt": "2026-06-03T18:00:00"
}
```

---

## 5. 알림 / Outbox 운영

### 화면 목적

알림 생성량, Outbox 성공률, 실패 원인, 무효 토큰을 확인합니다.

### API

```http
GET /api/v1/admin/operations/notifications?days=30
```

### Query

| name | type | required | default |
|---|---:|---:|---:|
| `days` | number | no | `30` |

### 주요 UI

- Notification 생성 수
- Outbox 상태별 수
- 발송 성공률
- 실패 사유별 수
- 무효 토큰 수
- 평균 발송 처리 시간

### Response `data`

```json
{
  "days": 30,
  "since": "2026-05-04T18:00:00",
  "notificationCreatedCount": 300,
  "outboxStatusCounts": [
    { "status": "PENDING", "count": 3 },
    { "status": "PROCESSING", "count": 1 },
    { "status": "SENT", "count": 240 },
    { "status": "FAILED", "count": 2 },
    { "status": "SKIPPED", "count": 14 }
  ],
  "deliverySuccessRatePercentage": 94,
  "failureReasons": [
    { "reason": "FCM_UNREGISTERED", "count": 2 },
    { "reason": "UNKNOWN", "count": 1 }
  ],
  "invalidTokenCount": 2,
  "averageProcessingSeconds": 0.8,
  "generatedAt": "2026-06-03T18:00:00"
}
```

---

## 6. 데이터 정합성 점검

### 화면 목적

DB/Outbox/티켓 원장/채팅방 참조의 이상 여부를 체크리스트로 보여줍니다.

### API

```http
GET /api/v1/admin/operations/integrity
```

### 주요 UI

- PASS / FAIL 요약 카드
- 점검 항목별 테이블
- `FAIL` 항목 강조

### Response `data`

```json
{
  "checks": [
    {
      "key": "chat_rooms_without_members",
      "label": "고아 채팅방",
      "status": "PASS",
      "count": 0,
      "description": "참여 중인 멤버가 없는 채팅방입니다."
    },
    {
      "key": "ticket_ledger_amount_mismatch",
      "label": "티켓 원장 금액 불일치",
      "status": "FAIL",
      "count": 1,
      "description": "afterAmount가 beforeAmount + changeAmount와 다른 원장입니다."
    }
  ],
  "warningCount": 0,
  "failureCount": 1,
  "generatedAt": "2026-06-03T18:00:00"
}
```

### Screenshot Target

```text
data-integrity.png
```

---

## 7. 운영 관리 지표

### 화면 목적

신고 처리, 제재, 공지, 관리자 조작량을 요약합니다.

### API

```http
GET /api/v1/admin/operations/management
```

### 주요 UI

- 신고 접수/처리/미처리
- 평균 신고 처리 시간
- 정지/매칭 제한/해제 수
- 공지 발송 수
- 공지 대상자 수
- 관리자 조작 이력 수

### Response `data`

```json
{
  "reportReceivedCount": 20,
  "reportProcessedCount": 14,
  "reportUnresolvedCount": 6,
  "averageReportProcessingSeconds": 3600.0,
  "suspendedActionCount": 3,
  "matchingRestrictedActionCount": 2,
  "matchingRestrictionClearedActionCount": 1,
  "noticeBroadcastCount": 5,
  "noticeRecipientCount": 110,
  "auditLogCount": 42,
  "generatedAt": "2026-06-03T18:00:00"
}
```

---

## 8. 관리자 Audit Log

### 화면 목적

관리자가 수행한 주요 조작 이력을 조회합니다.

### API

```http
GET /api/v1/admin/audit-logs?page=0&size=20
```

### Query

| name | type | required | default |
|---|---:|---:|---:|
| `page` | number | no | `0` |
| `size` | number | no | `20` |
| `actorUserId` | number | no | - |
| `action` | string | no | - |
| `targetType` | string | no | - |

### Action Values

```text
DASHBOARD_VIEWED
OPERATIONS_SUMMARY_VIEWED
OUTBOX_MONITOR_VIEWED
MATCHING_FUNNEL_VIEWED
GROUP_MATCHING_FUNNEL_VIEWED
NOTIFICATION_OPERATIONS_VIEWED
OPERATIONS_MANAGEMENT_VIEWED
INTEGRITY_CHECK_VIEWED
USER_ACTION_APPLIED
USER_PERMANENTLY_DELETED
REPORT_STATUS_UPDATED
TICKET_ADJUSTED
NOTICE_BROADCASTED
MAINTENANCE_UPDATED
```

### Response `data`

```json
{
  "items": [
    {
      "auditLogId": 1,
      "actorUserId": 999,
      "action": "TICKET_ADJUSTED",
      "targetType": "USER",
      "targetId": "7",
      "summary": "사용자 #7 티켓을 5만큼 조정했습니다.",
      "reason": "오류 보상",
      "metadataJson": "{\"amount\":5,\"beforeTickets\":10,\"afterTickets\":15}",
      "createdAt": "2026-06-03T18:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "hasNext": true
}
```

### Screenshot Target

```text
audit-log.png
```

---

## 9. 탈퇴 사용자 영구 삭제

### 화면 목적

탈퇴한 사용자의 DB 행과 재가입을 막는 관련 데이터를 삭제하여 같은 소셜 계정으로 재가입할 수 있게 합니다.

### API

```http
DELETE /api/v1/admin/users/{userId}/permanent
```

### 조건

- `status = DELETED`인 사용자만 삭제 가능합니다.
- 활성/정지/제한 사용자는 서버에서 거부합니다.

### 주요 UI

- 회원 상세에서 `status = DELETED`일 때만 버튼 노출
- 버튼명 예: `영구 삭제 및 재가입 허용`
- 위험 액션 확인 모달 필수

### Confirm Modal 문구 예

```text
이 작업은 탈퇴 사용자의 users 행과 재가입을 막는 관련 데이터를 삭제합니다.
삭제 후 같은 소셜 계정으로 다시 가입할 수 있습니다.
이 작업은 되돌릴 수 없습니다.
```

### Response `data`

```json
{
  "userId": 7,
  "provider": "APPLE",
  "socialId": "apple-sub",
  "status": "DELETED",
  "deletedProfileRows": 1,
  "deletedSchoolConsentRows": 1,
  "deletedChatRoomMemberRows": 1,
  "deletedRefreshTokenRows": 0,
  "deletedSocialDeviceBindingRows": 1,
  "deletedPushDeviceRows": 1,
  "deletedNotificationPreferenceRows": 1,
  "deletedNotificationRows": 3,
  "deletedNotificationOutboxRows": 3,
  "deletedPushEventRows": 0,
  "deletedUserMilestoneRows": 2,
  "userDeleted": true
}
```

### Frontend 처리

- 성공 후 회원 목록으로 이동하거나 상세 화면을 `삭제 완료` 상태로 전환합니다.
- 성공 toast 예:

```text
탈퇴 사용자를 영구 삭제했습니다. 이제 동일 소셜 계정으로 재가입할 수 있습니다.
```
