# API Error Codes By API

코드베이스 기준으로 정리한 API별 에러코드 인벤토리입니다.

기준:

- `src/main/java`의 Controller, ErrorCode enum, GlobalExceptionHandler 기준
- 공통 에러는 별도 섹션으로 묶고, API 그룹별 전용 에러코드를 매핑
- 일부 API는 전용 enum 없이 `COMMON-*`, `USER_*`, `AUTH_*`만 사용합니다

## 1. 공통 에러 코드

아래 코드는 여러 API에서 공통으로 내려갈 수 있습니다.

### 1-1. 전역 공통

- `400 BAD_REQUEST`: `COMMON-001`
- `401 UNAUTHORIZED`: `AUTH-001`
- `403 FORBIDDEN`: `AUTH-002`, `MOD-001`
- `404 NOT_FOUND`: `COMMON-404`
- `405 METHOD_NOT_ALLOWED`: `COMMON-405`
- `503 SERVICE_UNAVAILABLE`: `COMMON-503`
- `500 INTERNAL_SERVER_ERROR`: `COMMON-999`

### 1-2. 사용자 상태/필터 공통

- `403 FORBIDDEN`: `USER_DELETED`, `USER_SUSPENDED`, `USER_RESTRICTED`, `SCHOOL_EMAIL_VERIFICATION_REQUIRED`

설명:

- `USER_DELETED`, `USER_SUSPENDED`, `USER_RESTRICTED`는 인증 후 서비스 로직에서 자주 사용됩니다.
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`는 학교 이메일 인증 필터에 걸리는 API에서 내려갑니다.

## 2. 도메인별 전용 에러 코드

### 2-1. AuthErrorCode

- `400 BAD_REQUEST`: `AUTH_INVALID_LOGIN_REQUEST`, `AUTH_INVALID_REFRESH_REQUEST`, `AUTH_INVALID_LOGOUT_REQUEST`, `AUTH_SOCIAL_PROVIDER_REQUIRED`, `AUTH_SOCIAL_TOKEN_REQUIRED`, `AUTH_KAKAO_LOGIN_DISABLED`, `AUTH_ADMIN_LOGIN_DISABLED`, `AUTH_DEVICE_ID_REQUIRED`, `AUTH_REFRESH_TOKEN_REQUIRED`, `AUTH_EMAIL_PASSWORD_REQUIRED`, `AUTH_INVALID_PASSWORD_FORMAT`
- `401 UNAUTHORIZED`: `AUTH_EMAIL_LOGIN_FAILED`, `AUTH_EXPIRED_TOKEN`, `AUTH_INVALID_TOKEN`, `AUTH_INVALID_APPLE_TOKEN`, `AUTH_INVALID_ACCESS_TOKEN_TYPE`, `AUTH_INVALID_REFRESH_TOKEN_TYPE`, `AUTH_NOT_REFRESH_TOKEN`, `AUTH_DEVICE_MISMATCH`, `AUTH_REFRESH_TOKEN_NOT_FOUND`, `AUTH_REFRESH_TOKEN_MISMATCH`, `AUTH_REFRESH_TOKEN_REUSE_DETECTED`
- `403 FORBIDDEN`: `USER_DELETED`, `USER_SUSPENDED`, `USER_RESTRICTED`
- `404 NOT_FOUND`: `AUTH_EMAIL_ACCOUNT_NOT_FOUND`, `AUTH_USER_NOT_FOUND`
- `409 CONFLICT`: `AUTH_EMAIL_ALREADY_REGISTERED`
- `429 TOO_MANY_REQUESTS`: `AUTH_EMAIL_LOGIN_TEMPORARILY_LOCKED`

### 2-2. UserErrorCode

- `400 BAD_REQUEST`: `REQUIRED_CONSENT_NOT_ACCEPTED`, `PASSWORD_CHANGE_NOT_ALLOWED`, `PASSWORD_REQUIRED`, `PASSWORD_INVALID_FORMAT`, `PROFILE_IMAGE_EMPTY`, `PROFILE_IMAGE_TOO_LARGE`, `PROFILE_IMAGE_UNSUPPORTED_FORMAT`, `PROFILE_IMAGE_CORRUPTED`, `INVALID_INPUT`
- `403 FORBIDDEN`: `USER_DELETED`, `USER_SUSPENDED`, `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- `404 NOT_FOUND`: `USER_NOT_FOUND`
- `500 INTERNAL_SERVER_ERROR`: `PROFILE_IMAGE_STORAGE_ERROR`

### 2-3. MatchingErrorCode

- `400 BAD_REQUEST`: `PROFILE_REQUIRED`, `PROFILE_GENDER_REQUIRED`, `INSUFFICIENT_TICKETS`, `INVALID_TARGET`, `CANDIDATE_NOT_EXPOSED`, `INVALID_REQUEST`, `ALREADY_CONNECTED`
- `403 FORBIDDEN`: `MATCHING_RESTRICTED`, `BLOCKED_USER_INTERACTION`
- `404 NOT_FOUND`: `CONNECTION_NOT_FOUND`, `USER_NOT_FOUND`

### 2-4. VerificationErrorCode

- `400 BAD_REQUEST`: `VERIFY_INVALID_EMAIL_DOMAIN`, `VERIFY_INVALID_EMAIL_FORMAT`, `VERIFY_CODE_EXPIRED`, `VERIFY_CODE_MISMATCH`, `VERIFY_VERIFIED_EMAIL_TOKEN_REQUIRED`, `VERIFY_VERIFIED_EMAIL_TOKEN_EXPIRED`, `VERIFY_VERIFIED_EMAIL_MISMATCH`
- `409 CONFLICT`: `VERIFY_ALREADY_REGISTERED_EMAIL`, `VERIFY_VERIFIED_EMAIL_ALREADY_ISSUED`
- `429 TOO_MANY_REQUESTS`: `VERIFY_TOO_MANY_REQUESTS`, `VERIFY_TOO_MANY_ATTEMPTS`
- `500 INTERNAL_SERVER_ERROR`: `VERIFY_MAIL_SEND_FAILED`

### 2-5. ModerationErrorCode

- `400 BAD_REQUEST`: `MOD_REPORT_SELF_NOT_ALLOWED`, `MOD_BLOCK_SELF_NOT_ALLOWED`
- `403 FORBIDDEN`: `MOD_BLOCKED_INTERACTION`
- `404 NOT_FOUND`: `MOD_REPORT_TARGET_NOT_FOUND`, `MOD_REPORTER_NOT_FOUND`, `MOD_BLOCK_TARGET_NOT_FOUND`, `MOD_BLOCKER_NOT_FOUND`
- `409 CONFLICT`: `MOD_REPORT_DUPLICATE`

### 2-6. Group Matching / ErrorCode

과팅 API는 별도 enum이 아니라 `ErrorCode` 안의 `GMATCH-*`와 일부 공통 코드를 사용합니다.

- `400 BAD_REQUEST`: `GMATCH-004`, `GMATCH-005`, `GMATCH-006`, `GMATCH-007`, `GMATCH-008`, `GMATCH-009`, `GMATCH-010`, `GMATCH-011`, `GMATCH-012`, `GMATCH-013`, `GMATCH-017`, `GMATCH-018`, `GMATCH-020`, `GMATCH-021`, `GMATCH-022`, `GMATCH-024`, `GMATCH-025`, `GMATCH-026`, `GMATCH-030`, `GMATCH-031`, `GMATCH-032`
- `403 FORBIDDEN`: `GMATCH-015`, `GMATCH-016`, `GMATCH-019`, `GMATCH-023`
- `404 NOT_FOUND`: `GMATCH-001`, `GMATCH-002`, `GMATCH-003`
- `409 CONFLICT`: `GMATCH-014`
- `500 INTERNAL_SERVER_ERROR`: `GMATCH-027`, `GMATCH-028`, `GMATCH-029`

### 2-7. AdsErrorCode

- `400 BAD_REQUEST`: `AD_REWARD_INVALID_SESSION`, `AD_REWARD_SESSION_EXPIRED`, `AD_REWARD_INVALID_CALLBACK`
- `401 UNAUTHORIZED`: `AD_REWARD_INVALID_SIGNATURE`
- `409 CONFLICT`: `AD_REWARD_DUPLICATE_TRANSACTION`, `AD_REWARD_DUPLICATE_REQUEST`
- `429 TOO_MANY_REQUESTS`: `AD_REWARD_DAILY_LIMIT_EXCEEDED`

### 2-8. IapErrorCode

- `200 OK`: `IAP_ALREADY_PROCESSED`
- `400 BAD_REQUEST`: `IAP_INVALID_PRODUCT`, `IAP_INVALID_TRANSACTION`, `IAP_ENVIRONMENT_MISMATCH`, `IAP_ACCOUNT_TOKEN_MISMATCH`, `IAP_PROVIDER_NOT_SUPPORTED`
- `401 UNAUTHORIZED`: `IAP_UNAUTHORIZED`
- `403 FORBIDDEN`: `IAP_FORBIDDEN`
- `404 NOT_FOUND`: `IAP_ORDER_NOT_FOUND`
- `409 CONFLICT`: `IAP_DUPLICATE_REQUEST`
- `502 BAD_GATEWAY`: `IAP_STORE_VERIFY_FAILED`, `IAP_APPLE_VERIFY_FAILED`, `IAP_GOOGLE_VERIFY_FAILED`

### 2-9. CompatibilityErrorCode

- `400 BAD_REQUEST`: `COMPATIBILITY_INVALID_TARGET`, `COMPATIBILITY_PROFILE_REQUIRED`, `COMPATIBILITY_PROFILE_INCOMPLETE`, `COMPATIBILITY_USER_INACTIVE`
- `404 NOT_FOUND`: `COMPATIBILITY_USER_NOT_FOUND`

## 3. API 그룹별 에러 코드 매핑

## 3-1. Auth API

Base path:

- `/api/v1/auth`

Endpoints:

- `POST /social/login`
- `POST /email/login`
- `POST /refresh`
- `POST /logout`

주요 에러 코드:

- AuthErrorCode 전체
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `409`, `429`, `500`

특히 자주 보는 코드:

- `POST /social/login`
  - `AUTH_INVALID_LOGIN_REQUEST`
  - `AUTH_SOCIAL_PROVIDER_REQUIRED`
  - `AUTH_SOCIAL_TOKEN_REQUIRED`
  - `AUTH_KAKAO_LOGIN_DISABLED`
  - `AUTH_DEVICE_ID_REQUIRED`
  - `USER_DELETED`
  - `USER_SUSPENDED`
  - `USER_RESTRICTED`
- `POST /email/login`
  - `AUTH_ADMIN_LOGIN_DISABLED`
  - `AUTH_EMAIL_PASSWORD_REQUIRED`
  - `AUTH_EMAIL_LOGIN_FAILED`
  - `AUTH_EMAIL_LOGIN_TEMPORARILY_LOCKED`
  - `AUTH_DEVICE_ID_REQUIRED`
  - `USER_DELETED`
  - `USER_SUSPENDED`
  - `USER_RESTRICTED`
- `POST /refresh`
  - `AUTH_INVALID_REFRESH_REQUEST`
  - `AUTH_REFRESH_TOKEN_REQUIRED`
  - `AUTH_INVALID_REFRESH_TOKEN_TYPE`
  - `AUTH_NOT_REFRESH_TOKEN`
  - `AUTH_DEVICE_MISMATCH`
  - `AUTH_REFRESH_TOKEN_NOT_FOUND`
  - `AUTH_REFRESH_TOKEN_REUSE_DETECTED`
  - `AUTH_EXPIRED_TOKEN`
  - `AUTH_INVALID_TOKEN`
- `POST /logout`
  - `AUTH_INVALID_LOGOUT_REQUEST`

## 3-2. User API

Base paths:

- `/api/v1/users`
- `/api/v1/user`
- `/api/v1/users/profile-images`

Endpoints:

- `POST /api/v1/users/sign-up`
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me/nickname`
- `GET /api/v1/users/profile`
- `PATCH /api/v1/users/profile`
- `POST /api/v1/users/profile-image`
- `DELETE /api/v1/users/me`
- `GET /api/v1/users/school-consent`
- `PUT /api/v1/users/school-consent`
- `GET /api/v1/user/me`
- `GET /api/v1/users/profile-images/{fileName}`

주요 에러 코드:

- UserErrorCode 전체
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

추가 참고:

- 인증이 필요한 API는 `AUTH-001`, `AUTH-002` 가능
- 학교 이메일 미인증 사용자는 `SCHOOL_EMAIL_VERIFICATION_REQUIRED` 가능

## 3-3. Verification API

Base path:

- `/api/v1/verification`

Endpoints:

- `POST /email/send`
- `POST /email/verify`

주요 에러 코드:

- VerificationErrorCode 전체
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `404`, `405`, `409`, `429`, `500`

## 3-4. Matching API

Base path:

- `/api/v1/matching`

Endpoints:

- `GET /recommendations`
- `GET /recommendations/same-gender`
- `POST /connect/{targetUserId}`
- `GET /requests`
- `POST /accept/{connectionId}`
- `POST /reject/{connectionId}`

주요 에러 코드:

- MatchingErrorCode 전체
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

## 3-5. Group Matching API

Base path:

- `/api/v1/matching/team-rooms`

Endpoints:

- `POST /`
- `GET /public`
- `GET /recruitable`
- `POST /{teamRoomId}/join`
- `POST /join-by-invite`
- `POST /{teamRoomId}/invite-code`
- `GET /{teamRoomId}/members/{targetUserId}/profile`
- `GET /{teamRoomId}/chat/messages`
- `POST /{teamRoomId}/chat/messages`
- `PATCH /{teamRoomId}/chat/read`
- `GET /me`
- `GET /me/state`
- `PATCH /{teamRoomId}/ready`
- `POST /{teamRoomId}/queue/start`
- `GET /{teamRoomId}/queue`
- `POST /{teamRoomId}/queue/leave`
- `POST /{teamRoomId}/leave`
- `DELETE /{teamRoomId}/members/{targetUserId}`
- `PATCH /{teamRoomId}/visibility`
- `DELETE /{teamRoomId}`
- `GET /{teamRoomId}/final-room`

주요 에러 코드:

- `GMATCH-*` 전체
- `AUTH-001`
- `AUTH-002`
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-503`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `409`, `503`, `500`

## 3-6. Moderation API

Base path:

- `/api/v1/moderation`

Endpoints:

- `POST /reports`
- `GET /reports/me`
- `POST /blocks/{blockedUserId}`
- `DELETE /blocks/{blockedUserId}`
- `GET /blocks`
- `GET /blocks/{targetUserId}`
- `GET /support`

주요 에러 코드:

- ModerationErrorCode 전체
- `MOD-001` (`USER_BLOCKED_INTERACTION`)
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `409`, `500`

## 3-7. Compatibility API

Base path:

- `/api/v1/compatibility`

Endpoints:

- `GET /{targetUserId}`

주요 에러 코드:

- CompatibilityErrorCode 전체
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

## 3-8. Ads API

Base path:

- `/api/v1/ads/rewards`

Endpoints:

- `POST /session`
- `GET /callback/admob`

주요 에러 코드:

- AdsErrorCode 전체
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `404`, `405`, `409`, `429`, `500`

## 3-9. IAP API

Base path:

- `/api/v1/iap`

Endpoints:

- `POST /ios/transactions/verify`
- `POST /ios/transactions/sync`
- `GET /ios/transactions/{transactionId}`
- `POST /android/purchases/verify`
- `POST /android/purchases/sync`
- `GET /android/purchases/{purchaseToken}`
- `POST /ios/notifications`
- `POST /android/notifications`

주요 에러 코드:

- IapErrorCode 전체
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `200`, `400`, `401`, `403`, `404`, `405`, `409`, `502`, `500`

## 3-10. Admin API

Base paths:

- `/api/v1/admin`
- `/api/v1/admin/maintenance`

Endpoints:

- `GET /users`
- `GET /users/{userId}`
- `PATCH /users/{userId}/actions`
- `GET /matchings`
- `GET /reports`
- `PATCH /reports/{reportId}`
- `GET /tickets/users/{userId}`
- `GET /tickets/users/{userId}/ledger`
- `POST /tickets/adjustments`
- `GET /statistics/overview`
- `GET /notices`
- `GET /notices/{noticeId}`
- `POST /notices/broadcast`
- `GET /maintenance`
- `PATCH /maintenance`

주요 에러 코드:

- 공통 인증/권한: `AUTH-001`, `AUTH-002`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`
- 일부 서비스 로직:
  - `COMMON-404` (사용자/신고/공지 미존재)
  - `COMMON-001` (잘못된 요청)

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

참고:

- Admin API는 전용 `AdminErrorCode` enum이 없습니다.
- 주로 `BusinessException(ErrorCode.*)`와 인증 필터 기반 에러를 사용합니다.

## 3-11. Notification API

Base paths:

- `/api/v1/notifications`
- `/api/v1/push/devices`
- `/v1/push/devices`
- `/api/v1/push/events`
- `/v1/push/events`

Endpoints:

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `PATCH /api/v1/notifications/{notificationId}/read`
- `PATCH /api/v1/notifications/read-all`
- `DELETE /api/v1/notifications/{notificationId}`
- `GET /api/v1/notifications/preferences`
- `PATCH /api/v1/notifications/preferences`
- `GET /api/v1/push/devices`
- `POST /api/v1/push/devices`
- `PATCH /api/v1/push/devices/{deviceId}`
- `PATCH /api/v1/push/devices/{deviceId}/permission`
- `DELETE /api/v1/push/devices/{deviceId}`
- `POST /api/v1/push/events`

주요 에러 코드:

- 전용 enum 없음
- 공통 인증/권한: `AUTH-001`, `AUTH-002`
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

## 3-12. Chat API

Base paths:

- `/api/v1/chat`
- `/api/v1/chat/ops`

Endpoints:

- `GET /ops/stomp`
- `POST /rooms`
- `POST /rooms/{roomId}/join`
- `POST /rooms/{roomId}/leave`
- `GET /rooms`
- `GET /rooms/{roomId}/messages`
- `GET /rooms/{roomId}/counterpart-profile`
- `GET /rooms/{roomId}/participants/profiles`
- `GET /rooms/{roomId}/participants/{targetUserId}/profile`
- `POST /rooms/{roomId}/messages`
- `DELETE /rooms/{roomId}/messages/{messageId}`
- `PATCH /rooms/{roomId}/read`

주요 에러 코드:

- 전용 enum 없음
- 공통 인증/권한: `AUTH-001`, `AUTH-002`
- `SCHOOL_EMAIL_VERIFICATION_REQUIRED`
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-999`

대표 HTTP 상태:

- `400`, `401`, `403`, `404`, `405`, `500`

## 3-13. Statistics / Notice / Analytics / Maintenance 공개 API

Base paths:

- `/api/v1/statistics`
- `/api/v1/notices`
- `/api/v1/analytics`
- `/api/v1/maintenance`

Endpoints:

- `GET /api/v1/statistics/main`
- `GET /api/v1/notices`
- `POST /api/v1/analytics/events`
- `GET /api/v1/maintenance`

주요 에러 코드:

- 전용 enum 없음
- 공통 전역: `COMMON-001`, `COMMON-404`, `COMMON-405`, `COMMON-503`, `COMMON-999`

대표 HTTP 상태:

- `400`, `404`, `405`, `503`, `500`

## 4. 실무 참고

- 프론트/문서에서 “에러코드 표”를 만들 때는 `공통 에러`와 `도메인 전용 에러`를 분리해서 관리하는 편이 좋습니다.
- Admin API, Notification API, Chat API 일부는 전용 enum이 없어서, 실제 메시지는 `BusinessException(ErrorCode.*)` 중심으로 확인해야 합니다.
- 이 문서는 “현재 코드 기준 인벤토리”이므로, 새 API 추가나 예외 추가 시 함께 갱신해야 합니다.
