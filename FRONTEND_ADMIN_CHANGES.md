# Frontend Admin API Changes

관리자 페이지 프론트에서 반영해야 할 변경사항만 정리한 문서입니다.

## 1. 관리자 로그인 엔드포인트 변경

기존:

```http
POST /api/v1/auth/email/login
```

변경:

```http
POST /api/v1/auth/admin/login
```

요청 body:

```json
{
  "email": "admin@example.com",
  "password": "your-password",
  "deviceId": "admin-web"
}
```

주의:

- `/api/v1/auth/email/login`은 더 이상 존재하지 않습니다.
- 관리자 페이지 로그인 요청 주소를 반드시 `/api/v1/auth/admin/login`으로 바꿔야 합니다.

## 2. 관리자 로그인 응답에서 refresh token 제거

관리자 로그인 응답은 이제 access token만 사용합니다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": null,
    "user": {
      "userId": 1,
      "role": "ADMIN"
    }
  }
}
```

프론트 반영 사항:

- 관리자 페이지에서는 `refreshToken`이 없어도 정상 처리해야 합니다.
- 관리자 페이지에서 `/api/v1/auth/refresh` 기반 자동 연장 로직을 쓰고 있다면 제거하거나 비활성화해야 합니다.
- 로그인 후에는 `accessToken`만 저장해서 `Authorization: Bearer ...`로 호출하면 됩니다.

## 3. 신고 상태 변경 API에 `reason` 필드 추가

엔드포인트:

```http
PATCH /api/v1/admin/reports/{reportId}
```

기존 body:

```json
{
  "status": "IN_REVIEW"
}
```

변경 body:

```json
{
  "status": "IN_REVIEW",
  "reason": "선택 입력"
}
```

규칙:

- `IN_REVIEW`: `reason` 선택
- `RESOLVED`: `reason` 필수
- `REJECTED`: `reason` 필수

예시:

```json
{
  "status": "RESOLVED",
  "reason": "대상이 30일 정지 처리되었습니다."
}
```

프론트 반영 사항:

- 신고 처리 모달/폼에 `reason` 입력란을 추가해야 합니다.
- `RESOLVED`, `REJECTED` 선택 시 `reason` 없으면 제출 막아야 합니다.
- `IN_REVIEW`는 기존처럼 바로 처리 가능하지만, 필요하면 메모 입력을 받을 수 있습니다.

## 4. 사용자 제재/티켓 지급 API는 요청 형식 유지

아래 API들은 프론트 요청 형식 변경은 없습니다.

- `PATCH /api/v1/admin/users/{userId}/actions`
- `POST /api/v1/admin/tickets/adjustments`

다만 서버에서 대상 유저에게 운영자 공지가 자동 발송됩니다.

프론트에서 추가로 할 일:

- 없음

## 5. 운영 공지성 알림 관련 참고

서버가 신고 처리, 정지, 매칭 제한, 티켓 조정 시 대상 유저에게 `SYSTEM_ANNOUNCEMENT` 알림을 자동 발송합니다.

관리자 프론트에서 별도로 공지 API를 추가 호출할 필요는 없습니다.
