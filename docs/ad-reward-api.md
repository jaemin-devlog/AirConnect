# AirConnect Ads Reward API

광고 시청 보상(티켓 지급) 관련 API 명세입니다.

- Base URL: `/api/v1/ads/rewards`
- 공통 응답 래퍼: `ApiResponse`
- 시간 포맷: ISO-8601 OffsetDateTime (예: `2026-04-03T09:10:11.123456Z`)

## 1) 보상 세션 생성

- Method: `POST`
- Path: `/api/v1/ads/rewards/session`
- Auth: `Bearer AccessToken` 필요 (`@CurrentUserId` 사용)
- Description: 광고 시청 전 보상 세션을 생성합니다.

### Request

Body 없음

### Response (200)

```json
{
  "success": true,
  "data": {
    "sessionKey": "7b4Sx1...40chars...Qk2",
    "rewardAmount": 1,
    "expiresAt": "2026-04-03T09:20:11.123456Z"
  },
  "error": null,
  "traceId": "3f3c7f4d-16a4-4ab6-8c35-5e7c4f2b9e22"
}
```

### Error

- `AD_REWARD_DAILY_LIMIT_EXCEEDED` (429): 일일 광고 보상 횟수 초과

---

## 2) AdMob SSV 콜백

- Method: `GET`
- Path: `/api/v1/ads/rewards/callback/admob`
- Description: AdMob SSV 콜백을 검증하고 티켓을 지급합니다.

현재 정책: `GET /api/v1/ads/rewards/callback/admob`는 AdMob SSV 서버 호출을 위해 `permitAll`(무인증 허용)입니다.
보안 검증은 Authorization이 아닌 SSV signature/session 검증으로 수행합니다.

### Query Parameters (주요)

- `custom_data` : 세션키 또는 `sessionKey=...` 형식 문자열
- `transaction_id` : 광고 거래 식별자
- `signature` : SSV 서명값
- `key_id` : 서명 검증 키 ID

예시:

```text
GET /api/v1/ads/rewards/callback/admob?custom_data=sessionKey%3Dabc123...&transaction_id=tx-20260403-001&signature=...&key_id=...
```

### Response (200, 신규 지급)

```json
{
  "success": true,
  "data": {
    "sessionKey": "abc123...",
    "transactionId": "tx-20260403-001",
    "grantStatus": "GRANTED",
    "grantedTickets": 1,
    "beforeTickets": 76,
    "afterTickets": 77,
    "ledgerId": "TICKET_LEDGER_1201",
    "processedAt": "2026-04-03T09:22:00.100000Z"
  },
  "error": null,
  "traceId": "b0fce40f-2f6b-41f9-a41b-bf221943f6f8"
}
```

### Response (200, 이미 지급됨)

```json
{
  "success": true,
  "data": {
    "sessionKey": "abc123...",
    "transactionId": "tx-20260403-001",
    "grantStatus": "ALREADY_GRANTED",
    "grantedTickets": 1,
    "beforeTickets": 76,
    "afterTickets": 77,
    "ledgerId": "TICKET_LEDGER_1201",
    "processedAt": "2026-04-03T09:22:10.100000Z"
  },
  "error": null,
  "traceId": "ce5b4f6c-e0d2-4f77-a704-00f9733278dd"
}
```

### Error

- `AD_REWARD_INVALID_SIGNATURE` (401): 서명 검증 실패
- `AD_REWARD_INVALID_CALLBACK` (400): 필수 파라미터 누락/형식 오류
- `AD_REWARD_INVALID_SESSION` (400): 세션 없음/유효하지 않음
- `AD_REWARD_SESSION_EXPIRED` (400): 세션 만료
- `AD_REWARD_DUPLICATE_TRANSACTION` (409): 이미 처리된 transaction_id
- `AD_REWARD_DUPLICATE_REQUEST` (409): 중복 지급 요청

실패 응답 예시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AD_REWARD_INVALID_SIGNATURE",
    "message": "광고 콜백 서명 검증에 실패했습니다.",
    "httpStatus": 401,
    "traceId": "92fdfacf-886e-49f4-bf4b-54e3a48878f1",
    "details": null
  },
  "traceId": "92fdfacf-886e-49f4-bf4b-54e3a48878f1"
}
```

---

## 비고

- `grantStatus`: `GRANTED` | `ALREADY_GRANTED`
- 보상 지급은 session + ledger 멱등성 기준으로 1회만 반영됩니다.
- 콜백 raw query는 서버에 저장되며, 길이 제한(최대 2000자)으로 저장됩니다.
