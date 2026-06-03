# AirConnect Admin API Spec

관리자 프론트 연동용 API 명세입니다.

### 인증

관리자 로그인 후 받은 `accessToken`을 모든 관리자 API 요청에 사용합니다.

```http
Authorization: Bearer {accessToken}
```

관리자 로그인은 refresh token을 발급하지 않습니다. 토큰 만료 시 재로그인 UX를 제공해야 합니다.

### 공통 응답 래퍼

`/api/v1/admin/**` API는 기본적으로 `ApiResponse<T>` 래퍼를 반환합니다.

```json
{
  "success": true,
  "data": {},
  "error": null,
  "traceId": "trace-id"
}
```

실패 예:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_REQUEST",
    "message": "요청 값이 올바르지 않습니다.",
    "status": 400,
    "traceId": "trace-id",
    "details": {}
  },
  "traceId": "trace-id"
}
```

### 페이지 응답

목록 API는 아래 형태의 `PageResponse<T>`를 `data`로 반환합니다.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "hasNext": true
}
```

---


## 1. 운영 요약 대시보드

### GET `/api/v1/admin/operations/summary`

운영 기간, 가입자, 활성 사용자, 매칭/채팅/신고/Outbox 핵심 지표를 반환합니다.

#### Response `data`

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

#### Metric 기준

- `operationStartedAt`: 가장 먼저 가입한 사용자 `createdAt`
- `operationDays`: 첫 가입일 기준 운영 일수
- `dailyActiveUsers`: 최근 1일 `lastActiveAt`
- `weeklyActiveUsers`: 최근 7일 `lastActiveAt`
- `monthlyActiveUsers`: 최근 30일 `lastActiveAt`
- `unresolvedReports`: `OPEN + IN_REVIEW`
- `outboxBacklog`: `PENDING + PROCESSING`

---

## 3. 매칭 퍼널

### GET `/api/v1/admin/operations/matching-funnel?days=30`

1:1 매칭 퍼널을 반환합니다.

#### Query

| name | type | required | default | note |
|---|---:|---:|---:|---|
| `days` | number | no | `30` | 1~365 사이로 보정 |

#### Response `data`

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

#### Metric 기준

- 추천 조회 수: `MATCH_RECOMMENDATION_REFRESHED` 이벤트 수
- 매칭 요청 수: `MATCH_REQUEST_SENT` 이벤트 수
- 수락 수: `MATCH_REQUEST_ACCEPTED` 이벤트 수
- 거절/만료 수: 현재는 `REJECTED` 상태 기준입니다. 서버에 `EXPIRED` 상태가 추가되면 같은 필드에 합산 예정입니다.
- 평균 응답 시간: `matching_connections.respondedAt - connectedAt`
- 채팅방 생성 수: `ACCEPTED`이면서 `chatRoomId != null`

---

## 4. 그룹 매칭 퍼널

### GET `/api/v1/admin/operations/group-matching-funnel?days=30`

그룹 매칭 운영 퍼널을 반환합니다.

#### Query

| name | type | required | default | note |
|---|---:|---:|---:|---|
| `days` | number | no | `30` | 1~365 사이로 보정 |

#### Response `data`

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

#### Metric 기준

- 팀방 생성 수: `matching_temporary_team_rooms.createdAt`
- 팀방 참여 수: `matching_temporary_team_members.joinedAt`
- 준비 완료 팀 수: 팀 정원만큼 `ready=true`인 팀
- 큐 진입 수: `queuedAt`이 존재하는 팀
- 매칭 성공 수: `matching_results.matchedAt`
- 최종 그룹 채팅방 생성 수: `matching_final_group_chat_rooms.createdAt`
- 평균 큐 대기 시간: `matchedAt - queuedAt`

---

## 5. 알림 / Outbox 운영

### GET `/api/v1/admin/operations/notifications?days=30`

알림 생성 및 Outbox 발송 운영 지표를 반환합니다.

#### Query

| name | type | required | default | note |
|---|---:|---:|---:|---|
| `days` | number | no | `30` | 1~365 사이로 보정 |

#### Response `data`

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

#### Metric 기준

- Notification 생성 수: `notifications.createdAt >= since`
- Outbox 상태별 수: 전체 Outbox 기준
- 발송 성공률: `SENT / (SENT + FAILED + SKIPPED)`
- 실패 사유별 수: `FAILED` 상태의 `lastErrorCode`
- 무효 토큰 수: `lastErrorCode` 또는 `lastErrorMessage`에 invalid/unregistered 계열이 포함된 건
- 평균 처리 시간: 최종 상태의 `updatedAt - createdAt`

### GET `/api/v1/admin/operations/outbox`

Outbox backlog와 최근 실패 작업을 더 자세히 반환합니다.

#### Response `data`

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

---

## 6. 운영 관리

### GET `/api/v1/admin/operations/management`

신고, 제재, 공지, 관리자 조작 이력 관련 지표를 반환합니다.

#### Response `data`

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

#### Metric 기준

- 신고 접수: 전체 `user_reports`
- 신고 처리: `RESOLVED + REJECTED`
- 신고 미처리: `OPEN + IN_REVIEW`
- 평균 신고 처리 시간: 처리된 신고의 `updatedAt - createdAt`
- 정지/매칭 제한/해제 수: 관리자 Audit Log의 `USER_ACTION_APPLIED` metadata 기준
- 공지 발송 수: `admin_notices.count`
- 공지 대상자 수: `admin_notices.recipientCount` 합계
- 관리자 조작 이력: `admin_audit_logs.count`

---

## 7. 데이터 정합성 점검

### GET `/api/v1/admin/operations/integrity`

운영 데이터의 정합성 점검 결과를 반환합니다.

#### Response `data`

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

#### 현재 점검 항목

- 고아 채팅방
- 개인 채팅방 멤버 수 불일치
- 수락 매칭의 채팅방 누락
- 매칭-채팅방 참조 불일치
- Outbox-Notification 불일치
- 티켓 원장 금액 불일치
- 티켓 원장 참조 중복

---

## 8. 관리자 조작 이력

### GET `/api/v1/admin/audit-logs`

관리자가 수행한 주요 조작 이력을 조회합니다.

#### Query

| name | type | required | default |
|---|---:|---:|---:|
| `page` | number | no | `0` |
| `size` | number | no | `20` |
| `actorUserId` | number | no | - |
| `action` | string | no | - |
| `targetType` | string | no | - |

#### `action` values

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

#### Response `data.items[]`

```json
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
```

---

## 9. 회원 관리

### GET `/api/v1/admin/users`

회원 목록 조회입니다.

#### Query

| name | type | required | default | note |
|---|---:|---:|---:|---|
| `page` | number | no | `0` | |
| `size` | number | no | `20` | max 100 |
| `status` | string | no | - | `ACTIVE`, `SUSPENDED`, `RESTRICTED`, `DELETED` 등 서버 enum |
| `keyword` | string | no | - | 이름/닉네임/이메일/socialId/학과 검색 |

### GET `/api/v1/admin/users/{userId}`

회원 상세 조회입니다.

#### Response 주요 필드

```json
{
  "userId": 7,
  "provider": "APPLE",
  "socialId": "apple-sub",
  "email": "user@example.com",
  "schoolName": "HANSEO",
  "deptName": "컴퓨터공학과",
  "nickname": "닉네임",
  "name": "이름",
  "studentNum": 20240001,
  "role": "USER",
  "status": "ACTIVE",
  "onboardingStatus": "FULL",
  "gender": "FEMALE",
  "tickets": 10,
  "createdAt": "2026-05-01T12:00:00",
  "lastActiveAt": "2026-06-03T18:00:00",
  "deletedAt": null,
  "suspendedUntil": null,
  "restrictedAt": null,
  "restrictedUntil": null,
  "restrictedReason": null,
  "openReportCount": 0,
  "purchaseHistories": [],
  "sentRequestHistories": [],
  "ticketUsageHistories": [],
  "apiUsageHistories": []
}
```

### PATCH `/api/v1/admin/users/{userId}/actions`

회원 상태 조치입니다.

#### Request

```json
{
  "action": "SUSPEND",
  "reason": "운영 정책 위반",
  "until": "2026-07-03T00:00:00"
}
```

#### `action` values

```text
SUSPEND
DELETE
REACTIVATE
RESTRICT_MATCHING
CLEAR_MATCHING_RESTRICTION
```

#### Notes

- `SUSPEND`, `RESTRICT_MATCHING`은 `reason`, `until`을 입력받는 UX를 권장합니다.
- 서버는 대상 사용자에게 운영 알림을 자동 발송합니다.
- 관리자 감사 로그에 기록됩니다.

### DELETE `/api/v1/admin/users/{userId}/permanent`

탈퇴 사용자를 DB에서 영구 삭제하여 재가입 가능하게 만듭니다.

#### 조건

- `status = DELETED`인 사용자만 삭제 가능합니다.
- `ACTIVE`, `SUSPENDED`, `RESTRICTED` 사용자는 거부됩니다.

#### Response `data`

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

#### Frontend UX 권장

- 버튼명: `영구 삭제 및 재가입 허용`
- 위험 액션이므로 확인 모달 필수
- 모달 문구 예:

```text
이 작업은 탈퇴 사용자의 users 행과 재가입을 막는 관련 데이터를 삭제합니다.
삭제 후 같은 소셜 계정으로 다시 가입할 수 있습니다.
이 작업은 되돌릴 수 없습니다.
```

---
