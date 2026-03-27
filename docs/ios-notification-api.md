# AirConnect iOS Push Notification API

버전: v1.1  
작성일: 2026-03-27

## 1. 목적

이 문서는 iOS 앱이 AirConnect 백엔드와 직접 연동하기 위한 푸시 알림 계약을 정의한다.

- iOS 앱은 `FCM registration token`을 발급받아 서버에 등록한다.
- 서버는 `FCM HTTP v1`로 발송한다.
- iOS 실기기 전달 경로는 `Backend -> FCM -> APNs -> iOS Device`이다.
- 서버는 `APNS direct provider` 등록을 받지 않는다.
- 서버가 받는 `provider`는 현재 `FCM`만 허용한다.

## 2. Firebase / iOS 전제 조건

### 2.1 백엔드

현재 서버는 `application-dev.yml` 기준으로 배포되며, 실행 프로필은 `dev`를 사용한다.
백엔드는 아래 환경변수가 설정되어 있어야 한다.

```bash
SPRING_PROFILES_ACTIVE=dev
PORT=8080

DB_HOST=db.example.internal
DB_PORT=3306
DB_NAME=airconnect
DB_USERNAME=airconnect_user
DB_PASSWORD=change-me

REDIS_URL=redis://redis.example.internal:6379

JWT_SECRET=replace-with-a-long-random-secret
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=3600
JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=2592000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=no-reply@example.com
MAIL_PASSWORD=change-me

PROFILE_IMAGE_DIR=/var/lib/airconnect/profile-images
PROFILE_IMAGE_URL_BASE=https://api.example.com/api/v1/users/profile-images

NOTIFICATION_OUTBOX_WORKER_ENABLED=true
NOTIFICATION_OUTBOX_WORKER_DELAY_MS=1000
NOTIFICATION_OUTBOX_WORKER_RECOVERY_DELAY_MS=60000

NOTIFICATION_PUSH_FCM_ENABLED=true
FIREBASE_APP_NAME=airconnect-notification
FIREBASE_PROJECT_ID=airconnect-19762
FIREBASE_CREDENTIALS_PATH=/app/secrets/firebase-service-account.json

TEAM_ID=YOUR_APPLE_TEAM_ID
KEY_ID=YOUR_APPLE_SIGNIN_KEY_ID
BUNDLE_ID=com.example.airconnect
KEY_PATH=/app/secrets/apple-signin-key.p8
```

### 2.2 iOS 앱

1. 백엔드와 동일한 Firebase 프로젝트를 사용해야 한다.
2. Firebase Console에서 APNs auth key를 업로드해야 한다.
3. iOS 앱은 Firebase Messaging을 초기화해야 한다.
4. 알림 권한을 요청해야 한다.
5. `FCM registration token`을 발급받아야 한다.
6. 필요 시 `APNs device token`도 함께 서버로 전송할 수 있다.

## 3. 인증 / 공통 응답 형식

### 3.1 공통 헤더

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyIn0.signature
Content-Type: application/json
X-Trace-Id: 9f5dcb6e-6c6d-4c44-94b4-8c0f0d4d2bc1
```

### 3.2 공통 성공 응답 Envelope

```json
{
  "success": true,
  "data": {
    "exampleField": "exampleValue"
  },
  "error": null,
  "traceId": "9f5dcb6e-6c6d-4c44-94b4-8c0f0d4d2bc1"
}
```

### 3.3 공통 오류 응답 Envelope

앱팀이 요청한 `code`, `message`는 아래 `error` 객체 안에 항상 포함된다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON-001",
    "message": "잘못된 요청입니다.",
    "httpStatus": 400,
    "traceId": "9f5dcb6e-6c6d-4c44-94b4-8c0f0d4d2bc1",
    "details": null
  },
  "traceId": "9f5dcb6e-6c6d-4c44-94b4-8c0f0d4d2bc1"
}
```

앱에서 안정적으로 사용해야 하는 필드는 다음 두 개다.

```json
{
  "code": "COMMON-001",
  "message": "잘못된 요청입니다."
}
```

## 4. Push Device API

백엔드는 아래 두 경로를 모두 지원한다.

- `POST /api/v1/push/devices`
- `POST /v1/push/devices`

동일하게 PATCH / DELETE도 두 경로 규칙을 따른다.

### 4.1 디바이스 토큰 등록

`POST /api/v1/push/devices`  
호환 별칭: `POST /v1/push/devices`

#### 요청 규칙

- `platform`: `IOS` 또는 `ANDROID`
- `deviceId`: 앱이 기기별로 안정적으로 관리하는 문자열
- `fcmToken`: 앱팀 요청안 기준 권장 필드명
- `pushToken`: 백엔드 내부 필드명, `fcmToken`과 동일 의미
- `pushEnabled`: 앱팀 요청안 기준 권장 필드명
- `notificationPermissionGranted`: 백엔드 내부 필드명, `pushEnabled`와 동일 의미
- `provider`: optional, 생략 시 서버가 `FCM`으로 처리
- `apnsToken`: optional, iOS 진단용 저장 필드

#### 권장 요청 JSON

```json
{
  "platform": "IOS",
  "deviceId": "ios-iphone15pm-001",
  "fcmToken": "fNQ1x9W7R6u1wAqKJmT4Pq:APA91bH7x0J2m8v3K6x1T9w2Y4s8D1n5U7p9Q3r6S2t4V8y1Z5a7B9c2D4e6F8g0H1i3J5k7L9m1N3p5Q7r9S1t3U5v7W9x1Y3",
  "pushEnabled": true,
  "appVersion": "1.0.3",
  "osVersion": "18.3.1",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "apnsToken": "6f2bd8ea3dd9b4c0a6af22f1e9c5d6b7a8c9d0e1f2233445566778899aabbccd",
  "lastSeenAt": "2026-03-27T12:40:51"
}
```

#### 서버가 실제로 허용하는 전체 요청 JSON

```json
{
  "deviceId": "ios-iphone15pm-001",
  "platform": "IOS",
  "provider": "FCM",
  "pushToken": "fNQ1x9W7R6u1wAqKJmT4Pq:APA91bH7x0J2m8v3K6x1T9w2Y4s8D1n5U7p9Q3r6S2t4V8y1Z5a7B9c2D4e6F8g0H1i3J5k7L9m1N3p5Q7r9S1t3U5v7W9x1Y3",
  "fcmToken": "fNQ1x9W7R6u1wAqKJmT4Pq:APA91bH7x0J2m8v3K6x1T9w2Y4s8D1n5U7p9Q3r6S2t4V8y1Z5a7B9c2D4e6F8g0H1i3J5k7L9m1N3p5Q7r9S1t3U5v7W9x1Y3",
  "apnsToken": "6f2bd8ea3dd9b4c0a6af22f1e9c5d6b7a8c9d0e1f2233445566778899aabbccd",
  "notificationPermissionGranted": true,
  "pushEnabled": true,
  "appVersion": "1.0.3",
  "osVersion": "18.3.1",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "lastSeenAt": "2026-03-27T12:40:51"
}
```

#### 응답 JSON

앱팀 요청안의 최소 보장 필드 `deviceId`, `tokenStatus`, `updatedAt`는 아래 응답에 모두 포함된다.

```json
{
  "success": true,
  "data": {
    "pushDeviceId": 14,
    "userId": 2,
    "deviceId": "ios-iphone15pm-001",
    "platform": "IOS",
    "provider": "FCM",
    "tokenStatus": "ACTIVE",
    "pushEnabled": true,
    "notificationPermissionGranted": true,
    "active": true,
    "apnsTokenRegistered": true,
    "appVersion": "1.0.3",
    "osVersion": "18.3.1",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "lastSeenAt": "2026-03-27T12:40:51",
    "lastTokenRefreshedAt": "2026-03-27T12:40:51",
    "deactivatedAt": null,
    "createdAt": "2026-03-27T12:39:48",
    "updatedAt": "2026-03-27T12:40:51"
  },
  "error": null,
  "traceId": "7d87dd62-1674-4c0e-a269-0ac0aa49573a"
}
```

### 4.2 디바이스 푸시 설정 변경

`PATCH /api/v1/push/devices/{deviceId}`  
호환 별칭:

- `PATCH /v1/push/devices/{deviceId}`
- `PATCH /api/v1/push/devices/{deviceId}/permission`
- `PATCH /v1/push/devices/{deviceId}/permission`

#### 권장 요청 JSON

```json
{
  "pushEnabled": false,
  "lastSeenAt": "2026-03-27T12:55:00"
}
```

#### 서버가 실제로 허용하는 전체 요청 JSON

```json
{
  "notificationPermissionGranted": false,
  "pushEnabled": false,
  "lastSeenAt": "2026-03-27T12:55:00"
}
```

#### 응답 JSON

```json
{
  "success": true,
  "data": {
    "pushDeviceId": 14,
    "userId": 2,
    "deviceId": "ios-iphone15pm-001",
    "platform": "IOS",
    "provider": "FCM",
    "tokenStatus": "ACTIVE",
    "pushEnabled": false,
    "notificationPermissionGranted": false,
    "active": true,
    "apnsTokenRegistered": true,
    "appVersion": "1.0.3",
    "osVersion": "18.3.1",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "lastSeenAt": "2026-03-27T12:55:00",
    "lastTokenRefreshedAt": "2026-03-27T12:40:51",
    "deactivatedAt": null,
    "createdAt": "2026-03-27T12:39:48",
    "updatedAt": "2026-03-27T12:55:00"
  },
  "error": null,
  "traceId": "e6a7fc99-8c55-4fd1-b39a-813b1df4f5b8"
}
```

### 4.3 디바이스 토큰 해제

`DELETE /api/v1/push/devices/{deviceId}`  
호환 별칭: `DELETE /v1/push/devices/{deviceId}`

#### 응답 JSON

```json
{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "4b4013fe-8f7d-4b2a-9c6f-25a56e96c5e0"
}
```

### 4.4 디바이스 목록 조회

`GET /api/v1/push/devices`  
호환 별칭: `GET /v1/push/devices`

#### 응답 JSON

```json
{
  "success": true,
  "data": {
    "count": 1,
    "items": [
      {
        "pushDeviceId": 14,
        "userId": 2,
        "deviceId": "ios-iphone15pm-001",
        "platform": "IOS",
        "provider": "FCM",
        "tokenStatus": "ACTIVE",
        "pushEnabled": true,
        "notificationPermissionGranted": true,
        "active": true,
        "apnsTokenRegistered": true,
        "appVersion": "1.0.3",
        "osVersion": "18.3.1",
        "locale": "ko-KR",
        "timezone": "Asia/Seoul",
        "lastSeenAt": "2026-03-27T12:40:51",
        "lastTokenRefreshedAt": "2026-03-27T12:39:48",
        "deactivatedAt": null,
        "createdAt": "2026-03-27T12:39:48",
        "updatedAt": "2026-03-27T12:40:51"
      }
    ]
  },
  "error": null,
  "traceId": "f998fe95-4fdd-4a62-8e84-6f63891b84d9"
}
```

## 5. Push Event 수집 API

`POST /api/v1/push/events`  
호환 별칭: `POST /v1/push/events`

### 요청 규칙

- `notificationId`: 푸시 payload의 `notificationId` 값을 그대로 보낸다.
- 현재 백엔드에서 발급하는 `notificationId`는 숫자형 내부 ID를 문자열로 직렬화한 값이다.
- `providerMessageId`: 서버가 FCM 발송 성공 후 저장한 provider message id
- `eventType`: `RECEIVED` 또는 `OPENED`
- `occurredAt`: 클라이언트 시점 ISO-8601 문자열
- `deviceId`: 토큰 등록 때 사용한 동일 `deviceId`

### 요청 JSON

```json
{
  "notificationId": "250",
  "providerMessageId": "projects/airconnect-19762/messages/659d5744-dd71-419f-badc-a44e6356cac7",
  "eventType": "OPENED",
  "occurredAt": "2026-03-27T04:00:00",
  "deviceId": "ios-iphone15pm-001"
}
```

### 응답 JSON

```json
{
  "success": true,
  "data": {
    "pushEventId": 31,
    "notificationId": "250",
    "providerMessageId": "projects/airconnect-19762/messages/659d5744-dd71-419f-badc-a44e6356cac7",
    "eventType": "OPENED",
    "deviceId": "ios-iphone15pm-001",
    "occurredAt": "2026-03-27T04:00:00",
    "storedAt": "2026-03-27T04:00:01"
  },
  "error": null,
  "traceId": "e8c0dd59-3402-446c-a0f3-e968119e17f7"
}
```

## 6. 서버 -> FCM data Payload 계약

중요: FCM `data` payload는 string map이므로, 숫자/불리언처럼 보이는 값도 앱에서는 문자열로 받아야 한다.

### 6.1 공통 고정 키

서버는 모든 푸시에 아래 키를 고정해서 넣는다.

```json
{
  "notificationId": "250",
  "type": "SYSTEM",
  "notificationType": "MATCH_REQUEST_ACCEPTED",
  "title": "매칭이 수락되었어요",
  "body": "지금 바로 대화를 시작해보세요.",
  "deeplink": "airconnect://chat/rooms/3004",
  "sentAt": "2026-03-27T13:00:00"
}
```

### 6.2 `type` 값 정의

- `CHAT_MESSAGE`
- `NOTICE`
- `SYSTEM`

현재 서버 매핑은 다음과 같다.

- `CHAT_MESSAGE_RECEIVED` -> `CHAT_MESSAGE`
- `SYSTEM_ANNOUNCEMENT` -> `NOTICE`
- `APPOINTMENT_REMINDER_1H` -> `NOTICE`
- `APPOINTMENT_REMINDER_10M` -> `NOTICE`
- 그 외 비즈니스 알림 -> `SYSTEM`

### 6.3 상세 타입 구분 키

앱이 세부 라우팅을 해야 한다면 `notificationType`을 사용한다.

예시 값:

- `MATCH_REQUEST_RECEIVED`
- `MATCH_REQUEST_ACCEPTED`
- `MATCH_REQUEST_REJECTED`
- `GROUP_MATCHED`
- `CHAT_MESSAGE_RECEIVED`
- `MILESTONE_REWARDED`
- `TEAM_READY_REQUIRED`
- `TEAM_ALL_READY`
- `TEAM_ROOM_CANCELLED`
- `TEAM_MEMBER_JOINED`
- `TEAM_MEMBER_LEFT`
- `APPOINTMENT_REMINDER_1H`
- `APPOINTMENT_REMINDER_10M`
- `SYSTEM_ANNOUNCEMENT`

### 6.4 예시 payload

#### CHAT_MESSAGE

```json
{
  "notificationId": "301",
  "type": "CHAT_MESSAGE",
  "notificationType": "CHAT_MESSAGE_RECEIVED",
  "title": "민수님이 메시지를 보냈어요",
  "body": "잠깐 통화 가능해요?",
  "deeplink": "airconnect://chat/rooms/930",
  "sentAt": "2026-03-27T13:20:00",
  "chatRoomId": "930",
  "messageId": "15008",
  "senderUserId": "55",
  "senderNickname": "Minsu",
  "messagePreview": "잠깐 통화 가능해요?"
}
```

#### NOTICE

```json
{
  "notificationId": "302",
  "type": "NOTICE",
  "notificationType": "SYSTEM_ANNOUNCEMENT",
  "title": "공지사항",
  "body": "서버 점검이 예정되어 있습니다.",
  "deeplink": "airconnect://announcements/9001",
  "sentAt": "2026-03-27T13:21:00",
  "source": "admin_console",
  "noticeId": "9001"
}
```

#### SYSTEM

```json
{
  "notificationId": "303",
  "type": "SYSTEM",
  "notificationType": "MATCH_REQUEST_ACCEPTED",
  "title": "매칭이 수락되었어요",
  "body": "지금 바로 대화를 시작해보세요.",
  "deeplink": "airconnect://chat/rooms/3004",
  "sentAt": "2026-03-27T13:22:00",
  "connectionId": "1201",
  "chatRoomId": "3004",
  "partnerUserId": "55",
  "partnerNickname": "Minsu"
}
```

## 7. 서버 처리 규칙

앱팀 요청안 기준으로 현재 서버는 아래 규칙으로 동작한다.

1. `(userId, deviceId)` 기준으로 upsert 한다.
2. 토큰이 바뀌면 최신 토큰으로 교체한다.
3. 멀티디바이스를 허용한다.
4. FCM 실패 코드 `UNREGISTERED`, `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` 수신 시 해당 토큰을 비활성화한다.
5. 푸시 발송 성공 시 `providerMessageId`를 저장한다.
6. 클라이언트는 push event 수집 API에서 `notificationId`, `providerMessageId`를 함께 보내 추적할 수 있다.

## 8. Notification Inbox API

푸시 수신 이후 앱 내 알림함은 아래 API를 사용한다.

### 8.1 알림 목록 조회

`GET /api/v1/notifications`

Query:

- `cursorId`: optional
- `size`: optional, default `20`, max `100`
- `unreadOnly`: optional, default `false`
- `type`: optional

예시:

```http
GET /api/v1/notifications?cursorId=250&size=20&unreadOnly=false&type=SYSTEM_ANNOUNCEMENT
```

응답:

```json
{
  "success": true,
  "data": {
    "requestedSize": 20,
    "unreadCount": 3,
    "hasNext": true,
    "nextCursorId": 231,
    "count": 2,
    "items": [
      {
        "notificationId": 250,
        "userId": 2,
        "type": "SYSTEM_ANNOUNCEMENT",
        "category": "SYSTEM",
        "title": "Web Push Test",
        "body": "This is a production-like test push.",
        "deeplink": "airconnect://notifications/250",
        "actorUserId": null,
        "imageUrl": "https://cdn.example.com/notifications/system-banner.png",
        "payload": {
          "type": "SYSTEM_ANNOUNCEMENT",
          "source": "admin_console",
          "deeplink": "airconnect://notifications/250",
          "noticeId": 9001,
          "display": {
            "badge": "NEW",
            "priority": "HIGH"
          }
        },
        "read": false,
        "readAt": null,
        "deleted": false,
        "createdAt": "2026-03-27T12:58:57.386666"
      },
      {
        "notificationId": 249,
        "userId": 2,
        "type": "SYSTEM_ANNOUNCEMENT",
        "category": "SYSTEM",
        "title": "Server Maintenance",
        "body": "Maintenance starts at 02:00 AM.",
        "deeplink": "airconnect://announcements/maintenance-20260327",
        "actorUserId": null,
        "imageUrl": "https://cdn.example.com/notifications/maintenance.png",
        "payload": {
          "type": "SYSTEM_ANNOUNCEMENT",
          "source": "admin_console",
          "deeplink": "airconnect://announcements/maintenance-20260327",
          "noticeId": 9000,
          "maintenanceWindow": {
            "startsAt": "2026-03-28T02:00:00",
            "endsAt": "2026-03-28T03:00:00"
          }
        },
        "read": true,
        "readAt": "2026-03-27T13:00:12",
        "deleted": false,
        "createdAt": "2026-03-27T12:57:45.771512"
      }
    ]
  },
  "error": null,
  "traceId": "03c0f9a0-2c30-4b60-a589-cd17af0efef6"
}
```

### 8.2 미읽음 개수

`GET /api/v1/notifications/unread-count`

```json
{
  "success": true,
  "data": {
    "unreadCount": 3
  },
  "error": null,
  "traceId": "b95c80b5-c0e4-448b-ab60-a09ef2e846fe"
}
```

### 8.3 단건 읽음 처리

`PATCH /api/v1/notifications/{notificationId}/read`

```json
{
  "success": true,
  "data": {
    "notificationId": 250,
    "userId": 2,
    "type": "SYSTEM_ANNOUNCEMENT",
    "category": "SYSTEM",
    "title": "Web Push Test",
    "body": "This is a production-like test push.",
    "deeplink": "airconnect://notifications/250",
    "actorUserId": null,
    "imageUrl": "https://cdn.example.com/notifications/system-banner.png",
    "payload": {
      "type": "SYSTEM_ANNOUNCEMENT",
      "source": "admin_console",
      "deeplink": "airconnect://notifications/250",
      "noticeId": 9001,
      "display": {
        "badge": "NEW",
        "priority": "HIGH"
      }
    },
    "read": true,
    "readAt": "2026-03-27T13:10:51.112",
    "deleted": false,
    "createdAt": "2026-03-27T12:58:57.386666"
  },
  "error": null,
  "traceId": "b7cc8d5f-6e89-4d87-8f95-d5810acd17d3"
}
```

### 8.4 전체 읽음 처리

`PATCH /api/v1/notifications/read-all`

```json
{
  "success": true,
  "data": {
    "readCount": 3,
    "unreadCount": 0
  },
  "error": null,
  "traceId": "9021f935-d8f2-4420-a6d6-af44b8e3b8fe"
}
```

### 8.5 알림 삭제

`DELETE /api/v1/notifications/{notificationId}`

```json
{
  "success": true,
  "data": null,
  "error": null,
  "traceId": "dcffbd14-c44a-4524-a8cf-52fb709dbf7f"
}
```

## 9. Notification Preference API

### 9.1 설정 조회

`GET /api/v1/notifications/preferences`

```json
{
  "success": true,
  "data": {
    "userId": 2,
    "pushEnabled": true,
    "inAppEnabled": true,
    "matchRequestEnabled": true,
    "matchResultEnabled": true,
    "groupMatchingEnabled": true,
    "chatMessageEnabled": true,
    "milestoneEnabled": true,
    "reminderEnabled": true,
    "quietHoursEnabled": true,
    "quietHoursStart": "23:00:00",
    "quietHoursEnd": "07:00:00",
    "timezone": "Asia/Seoul",
    "createdAt": "2026-03-20T09:10:20",
    "updatedAt": "2026-03-27T13:14:10"
  },
  "error": null,
  "traceId": "cb593b75-3403-4bcb-bb8b-8f4a84418e9b"
}
```

### 9.2 설정 변경

`PATCH /api/v1/notifications/preferences`

```json
{
  "pushEnabled": true,
  "inAppEnabled": true,
  "matchRequestEnabled": true,
  "matchResultEnabled": true,
  "groupMatchingEnabled": true,
  "chatMessageEnabled": false,
  "milestoneEnabled": true,
  "reminderEnabled": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "23:30:00",
  "quietHoursEnd": "07:30:00",
  "timezone": "Asia/Seoul"
}
```

```json
{
  "success": true,
  "data": {
    "userId": 2,
    "pushEnabled": true,
    "inAppEnabled": true,
    "matchRequestEnabled": true,
    "matchResultEnabled": true,
    "groupMatchingEnabled": true,
    "chatMessageEnabled": false,
    "milestoneEnabled": true,
    "reminderEnabled": true,
    "quietHoursEnabled": true,
    "quietHoursStart": "23:30:00",
    "quietHoursEnd": "07:30:00",
    "timezone": "Asia/Seoul",
    "createdAt": "2026-03-20T09:10:20",
    "updatedAt": "2026-03-27T13:16:45"
  },
  "error": null,
  "traceId": "ed178899-fdfd-4aa8-b2fb-58d7bd8e23f2"
}
```

## 10. iOS 클라이언트 구현 순서

1. Firebase Messaging 초기화
2. 알림 권한 요청
3. FCM registration token 획득
4. 로그인 직후 `POST /v1/push/devices` 호출
5. 토큰 변경 시 동일 API 재호출
6. 권한 변경 시 `PATCH /v1/push/devices/{deviceId}` 호출
7. 로그아웃 시 `DELETE /v1/push/devices/{deviceId}` 호출
8. 푸시 수신 시 payload의 `notificationId`, `providerMessageId`, `deviceId`를 이용해 `POST /v1/push/events` 호출
9. 알림 탭 진입 시 `GET /api/v1/notifications`
10. 배지 갱신 시 `GET /api/v1/notifications/unread-count`
11. 알림 상세 진입 시 `PATCH /api/v1/notifications/{notificationId}/read`

## 11. 앱팀 요청안 충족 여부

아래 항목은 현재 구현과 일치한다.

1. 토큰 등록 API 동작
2. 토큰 갱신 API 동작
3. 토큰 해제 API 동작
4. `(userId, deviceId)` 기준 upsert
5. 멀티디바이스 허용
6. `providerMessageId` 저장
7. `POST /v1/push/events` 수집 API 지원
8. 공통 FCM data payload 키 고정
9. `UNREGISTERED`, `INVALID_ARGUMENT` 실패 시 토큰 비활성화

추가 주의사항:

- `notificationId`는 현재 UUID가 아니라 문자열화된 숫자 ID이다.
- 서버 응답은 프로젝트 공통 `ApiResponse` envelope를 사용한다.
- 디바이스 등록 요청에서는 `fcmToken`과 `pushEnabled`를 권장한다.
