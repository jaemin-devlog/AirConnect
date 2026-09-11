# 1:1 매칭 모바일 API 변경 명세

대상: Android / iOS  
범위: 기존 앱에서 수정해야 하는 1:1 매칭 API 계약만 포함

## 1. 추천 API 요청 헤더

### 대상 API

- `GET /api/v1/matching/recommendations`
- `GET /api/v1/matching/recommendations/same-gender`

### 변경 사항

두 API 모두 아래 헤더를 반드시 전송합니다.

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

- UUID 사용을 권장합니다.
- 추천 화면을 새로 불러올 때마다 새 키를 생성합니다.
- 동일 호출이 타임아웃되어 재시도할 때는 기존 키를 그대로 사용합니다.
- 같은 키를 일반 추천과 동성 추천에 함께 사용하면 안 됩니다.

### 오류 처리

| HTTP | `error.code` | 앱 처리 |
| --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 키를 생성한 후 요청 재시도 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 새 키로 새로운 요청 실행 |

## 2. 추천 후보 응답 모델

추천 API의 `data.candidates[]` 모델을 다음과 같이 변경합니다.

### 제거 필드

- `socialId`
- `studentNum`
- `status` — 상대방 계정 상태
- `tickets` — 상대방 보유 티켓

위 필드를 Android/iOS의 필수 디코딩 모델에서 제거합니다.

### 추가 필드

| 필드 | 타입 | Nullable | 설명 |
| --- | --- | --- | --- |
| `admissionYear` | Integer | O | 입학 연도. 예: `2024` |

`admissionYear`가 `2024`이면 화면에는 `24학번`으로 표시합니다. `null`이면 학번 영역을 숨깁니다.

### 유지 필드

- `onboardingStatus`
- `emailVerified`
- `profileExists`
- `profileImageUploaded`
- `profile.instagram`
- `userId`, `age`, `nickname`, `deptName`, `profileImage`, `gender`, `profile`

### 응답 예시

```json
{
  "success": true,
  "data": {
    "recommendationRequestId": "550e8400-e29b-41d4-a716-446655440000",
    "count": 1,
    "candidates": [
      {
        "userId": 27,
        "admissionYear": 2024,
        "onboardingStatus": "FULL",
        "emailVerified": true,
        "profileExists": true,
        "profileImageUploaded": true,
        "age": 22,
        "nickname": "에어냥",
        "deptName": "항공소프트웨어공학과",
        "profileImage": "https://example.com/profile.jpg",
        "gender": "FEMALE",
        "profile": {
          "instagram": "airconnect"
        }
      }
    ],
    "userTicketsRemaining": 8
  },
  "error": null,
  "traceId": "trace-id"
}
```

`profile`에는 기존 프로필 필드가 그대로 함께 전달됩니다.

## 3. 매칭 요청 전송 API

```http
POST /api/v1/matching/connect/{targetUserId}
Idempotency-Key: <UUID>
```

### 요청 변경 사항

- `Idempotency-Key`를 반드시 전송합니다.
- 전송 버튼을 누를 때 키를 한 번 생성합니다.
- 네트워크 오류나 타임아웃으로 같은 전송을 재시도할 때는 같은 키를 사용합니다.
- 사용자가 새로 요청하는 동작에는 새 키를 사용합니다.
- 서로 다른 `targetUserId`에 같은 키를 사용하면 안 됩니다.

### 응답 변경 사항

`data`에 다음 필드를 추가합니다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `connectionId` | Long | 생성되었거나 기존에 존재하는 1:1 요청 ID |
| `userTicketsRemaining` | Integer | 요청 처리 후 로그인 사용자의 남은 티켓 |

```json
{
  "success": true,
  "data": {
    "connectionId": 123,
    "chatRoomId": null,
    "targetUserId": 27,
    "alreadyConnected": false,
    "userTicketsRemaining": 8
  },
  "error": null,
  "traceId": "trace-id"
}
```

### 앱 분기

- `alreadyConnected == false`: 요청 전송 완료 처리 후 티켓 UI를 `userTicketsRemaining`으로 갱신합니다.
- `alreadyConnected == true`: 추가 티켓 차감 없이 기존 연결입니다. `chatRoomId`가 있으면 기존 채팅방으로 이동할 수 있습니다.
- 응답을 받지 못한 경우 새로운 키를 만들지 말고 같은 키로 재시도합니다.

## 4. 보낸 요청 취소 API 추가

```http
DELETE /api/v1/matching/requests/{connectionId}
```

- 보낸 사람만 취소할 수 있습니다.
- 별도의 `Idempotency-Key` 헤더는 필요하지 않습니다.
- 성공하면 해당 요청 카드를 목록에서 제거합니다.

```json
{
  "success": true,
  "data": {
    "connectionId": 123,
    "targetUserId": 27,
    "chatRoomId": null,
    "status": "CANCELLED"
  },
  "error": null,
  "traceId": "trace-id"
}
```

## 5. 수락·거절·취소 응답 상태 변경

### 대상 API

- `POST /api/v1/matching/accept/{connectionId}`
- `POST /api/v1/matching/reject/{connectionId}`
- `DELETE /api/v1/matching/requests/{connectionId}`

### 공통 응답 모델

```json
{
  "connectionId": 123,
  "targetUserId": 27,
  "chatRoomId": 456,
  "status": "ACCEPTED"
}
```

`status` enum에 다음 두 값을 추가합니다.

- `CANCELLED`: 요청이 취소됨
- `EXPIRED`: 요청 가능 기간이 지남

최종 enum은 `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`입니다.

### 상태별 앱 처리

| `data.status` | 처리 |
| --- | --- |
| `ACCEPTED` | `chatRoomId`가 있을 때만 채팅방으로 이동 |
| `REJECTED` | 요청 카드 제거 |
| `CANCELLED` | 요청 카드 제거 |
| `EXPIRED` | 요청 카드 제거 후 `응답 기한이 지난 요청이에요` 안내 |

중요: 만료된 요청은 **HTTP 200으로도 `data.status = EXPIRED`가 반환될 수 있습니다.** HTTP 성공 여부만 보고 채팅방으로 이동하면 안 되며 반드시 `data.status`를 확인합니다.

### 반복 호출 처리

- 같은 요청을 다시 수락하면 기존 `ACCEPTED` 결과가 반환됩니다.
- 같은 요청을 다시 거절하면 기존 `REJECTED` 결과가 반환됩니다.
- 같은 요청을 다시 취소하면 기존 `CANCELLED` 결과가 반환됩니다.
- 이미 처리된 요청에 반대 동작을 수행하면 `INVALID_REQUEST` 오류가 반환됩니다.

앱은 네트워크 오류 후 동일 `connectionId`로 같은 동작을 안전하게 재시도할 수 있습니다.

## 6. 요청 목록 응답 모델

```http
GET /api/v1/matching/requests
```

`data.sent[]`와 `data.received[]`의 상대방 모델도 추천 후보와 동일하게 변경합니다.

### 제거 필드

- `socialId`
- `studentNum`
- `userStatus`
- `tickets`

### 추가 필드

- `admissionYear: Integer?`

### 유지 필드

- `onboardingStatus`
- `emailVerified`
- `profileExists`
- `profileImageUploaded`
- `profile.instagram`
- 요청 상태인 `status`

이 API는 현재 유효한 `PENDING` 요청만 반환합니다. 목록 새로고침 후 사라진 요청은 취소·만료·차단·계정 제재 등으로 더 이상 응답할 수 없는 요청이므로 로컬 목록에서도 제거합니다.

## 7. Android/iOS 적용 체크리스트

- [ ] `studentNum`을 제거하고 nullable `admissionYear`를 추가한다.
- [ ] `socialId`, 상대방 `tickets`, 상대방 계정 `status/userStatus`를 필수 디코딩 모델에서 제거한다.
- [ ] Instagram과 온보딩·이메일 인증·프로필 등록 관련 필드는 유지한다.
- [ ] 추천 2종과 요청 전송 API에 `Idempotency-Key`를 전송한다.
- [ ] 동일 동작 재시도에는 같은 키를 사용하고 새 동작에는 새 키를 사용한다.
- [ ] 요청 취소 API를 연결한다.
- [ ] 매칭 요청 상태 enum에 `CANCELLED`, `EXPIRED`를 추가한다.
- [ ] HTTP 200이어도 `EXPIRED`이면 채팅방으로 이동하지 않는다.
- [ ] 요청 전송 후 `userTicketsRemaining`으로 본인 티켓 UI를 갱신한다.
- [ ] 수락 시 `chatRoomId`가 null이 아닌 경우에만 채팅방으로 이동한다.

