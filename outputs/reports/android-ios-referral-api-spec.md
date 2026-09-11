# AirConnect Android/iOS 추천인 기능 API 명세

## 1. 기능 개요

추천인 기능은 회원가입·온보딩 화면이 아니라 **마이페이지**에서 제공합니다.

- 사용자는 본인의 추천 코드를 확인하고 친구에게 공유할 수 있습니다.
- 친구가 추천 코드를 처음 입력하면 친구와 추천인에게 티켓이 각각 5장 지급됩니다.
- 추천 코드는 **영문 대문자와 숫자를 모두 포함하는 6자리**입니다. 예: `A7B2C9`
- 추천 코드 입력은 계정당 한 번만 가능합니다.
- 추천할 수 있는 친구 수와 추천 코드의 유효기간에는 제한이 없습니다.
- 본인 코드 입력과 같은 두 사용자의 서로 맞추천은 허용하지 않습니다.

모든 API는 다음 헤더가 필요합니다.

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

별도의 `Idempotency-Key` 헤더는 필요하지 않습니다.

## 2. 내 추천인 정보 조회

### 요청

```http
GET /api/v1/referrals/me
Authorization: Bearer {accessToken}
```

### 성공 응답

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

### 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `referralCode` | String | 현재 사용자의 고유 추천 코드 |
| `rewardTickets` | Number | 한 명 추천 성공 시 양쪽에 지급되는 티켓 수 |
| `referredFriendCount` | Number | 내 코드를 사용한 친구의 누적 인원 |
| `hasEnteredReferralCode` | Boolean | 내가 이미 다른 사용자의 코드를 입력했는지 여부 |
| `canEnterReferralCode` | Boolean | 추천 코드 입력 가능 여부 |

기존 사용자에게 추천 코드가 아직 없으면 이 API를 처음 호출할 때 자동으로 생성됩니다. 이후에는 같은 코드가 계속 반환됩니다.

## 3. 추천인 코드 입력

### 요청

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

- 공백 없이 6자리로 전송합니다.
- 영문 소문자를 보내도 서버가 대문자로 변환합니다.
- 영문과 숫자가 각각 한 글자 이상 포함되어야 합니다.

### 처음 적용된 성공 응답

```json
{
  "success": true,
  "data": {
    "referralCode": "A7B2C9",
    "rewardTickets": 5,
    "myTickets": 15,
    "firstApplied": true,
    "redeemedAt": "2026-09-11T14:30:00.123456"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 같은 요청을 재전송한 성공 응답

서버에서 처리는 끝났지만 앱이 응답을 받지 못한 경우 같은 코드를 재전송해도 티켓은 다시 지급되지 않습니다.

```json
{
  "success": true,
  "data": {
    "referralCode": "A7B2C9",
    "rewardTickets": 5,
    "myTickets": 15,
    "firstApplied": false,
    "redeemedAt": "2026-09-11T14:30:00.123456"
  },
  "error": null,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `referralCode` | String | 적용된 추천 코드 |
| `rewardTickets` | Number | 양쪽에 지급된 티켓 수 |
| `myTickets` | Number | 처리 완료 후 현재 사용자의 총 티켓 수 |
| `firstApplied` | Boolean | 이번 요청에서 처음 적용됐으면 `true`, 동일 요청 재확인이면 `false` |
| `redeemedAt` | String | 최초 적용 시각, ISO-8601 로컬 날짜·시간 |

## 4. 오류 응답

공통 오류 형식은 다음과 같습니다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "REFERRAL-001",
    "message": "유효하지 않은 추천인 코드입니다.",
    "httpStatus": 400,
    "traceId": "550e8400-e29b-41d4-a716-446655440000",
    "details": null
  },
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| HTTP | 오류 코드 | 앱 표시 권장 문구 | 발생 조건 |
|---|---|---|---|
| 400 | `COMMON-001` | 추천인 코드 형식을 확인해 주세요. | 길이·문자 형식 또는 요청 본문 오류 |
| 400 | `REFERRAL-001` | 존재하지 않는 추천인 코드예요. | 형식은 맞지만 코드가 없음 |
| 400 | `REFERRAL-002` | 내 추천인 코드는 입력할 수 없어요. | 본인 코드 입력 |
| 409 | `REFERRAL-003` | 이미 추천인 코드를 입력했어요. | 이전과 다른 코드를 다시 입력 |
| 409 | `REFERRAL-004` | 서로의 추천인 코드를 중복 입력할 수 없어요. | 같은 두 사용자의 맞추천 |
| 403 | `REFERRAL-005` | 현재 계정에서는 추천인 기능을 사용할 수 없어요. | 온보딩 미완료 또는 비활성 계정 |
| 401 | `AUTH-001` | 다시 로그인해 주세요. | 인증 토큰 없음·만료 |
| 503 | `COMMON-503` | 서버 점검 중이에요. 잠시 후 다시 시도해 주세요. | 서버 점검 모드 |

## 5. 마이페이지 구현 흐름

1. 마이페이지의 추천인 메뉴 진입 시 `GET /api/v1/referrals/me`를 호출합니다.
2. `referralCode`와 “친구와 나 모두 티켓 5장” 문구를 표시하고 복사·공유 기능을 제공합니다.
3. `canEnterReferralCode=true`일 때만 코드 입력창과 등록 버튼을 표시합니다.
4. 등록 버튼을 누르면 중복 탭을 막고 `POST /api/v1/referrals/redeem`을 한 번 호출합니다.
5. 성공하면 `myTickets`로 앱의 티켓 잔액을 즉시 갱신합니다.
6. `firstApplied=true`이면 “친구와 나에게 티켓 5장이 지급됐어요”를 표시합니다.
7. `firstApplied=false`이면 중복 보상 안내 없이 이미 적용된 상태로 화면만 동기화합니다.
8. 적용 성공 후 입력창을 숨기고 `GET /api/v1/referrals/me`를 다시 호출하거나 로컬 상태를 `hasEnteredReferralCode=true`, `canEnterReferralCode=false`로 변경합니다.

## 6. 서버 보장 사항

- 추천인과 코드 입력자의 티켓 지급은 하나의 DB 트랜잭션으로 처리됩니다.
- 한쪽 지급이나 이력 저장이 실패하면 전체 작업이 취소됩니다.
- 같은 요청을 재전송하거나 동시에 여러 번 전송해도 한 번만 지급됩니다.
- 모든 지급 내역은 티켓 원장에 `REFERRAL_REWARD`로 기록됩니다.
