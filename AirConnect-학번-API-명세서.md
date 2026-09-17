# AirConnect 학번 API 명세서

- 기준 브랜치: `develop`
- 기준 커밋: `e0e5281` + 학번 응답 필드 통일 작업분
- Base URL: `https://airconnect.cloud`
- 작성일: 2026-09-18
- 대상: Android / iOS

## 1. 핵심 규칙

API 필드명은 `studentNum` 하나로 통일한다.

`studentNum`은 전체 학번이 아니라 **두 자리 입학 연도**이다.

| 입학 연도 | API 값 |
|---|---:|
| 2026학년도 | `26` |
| 2024학년도 | `24` |
| 2021학년도 | `21` |
| 2000학년도 | `0` |

프로필 상세 응답에서 기존에 `studentNum`과 함께 내려가던 `admissionYear`는 제거한다.

```json
{
  "studentNum": 26
}
```

앱 표시 예시:

```text
studentNum = 26 -> "26학번"
studentNum = 0  -> "00학번"
```

`studentNum`은 JSON Number 또는 앱의 nullable 정수형으로 처리한다. 문자열 `"26"`으로 보내지 않는다.

## 2. 회원가입 입력

```http
POST /api/v1/users/sign-up
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청 예시:

```json
{
  "name": "홍길동",
  "nickname": "길동",
  "studentNum": 26,
  "deptName": "컴퓨터공학과",
  "height": 175,
  "age": 23,
  "mbti": "ENFP",
  "smoking": "NO",
  "gender": "MALE",
  "military": "COMPLETED",
  "residence": "서울",
  "intro": "안녕하세요",
  "instagram": "example"
}
```

### 2.1 검증 규칙

| 입력 | 처리 결과 |
|---|---|
| `26` | 허용 |
| `0` | 허용, 2000학년도 의미 |
| `99` | 허용, 1999학년도 의미 |
| `2026` | 거절 |
| `202600123` | 거절 |
| `null` 또는 필드 누락 | 거절 |
| `-1` | 거절 |
| `100` | 거절 |

허용 범위는 정수 `0~99`이다. 형식이 잘못되면 `INVALID_INPUT` 오류가 반환된다.

## 3. 내 정보·마이페이지

### 3.1 내 전체 정보

```http
GET /api/v1/users/me
```

호환용 별칭:

```http
GET /api/v1/user/me
```

필드 위치:

```text
data.studentNum
```

```json
{
  "success": true,
  "data": {
    "userId": 10,
    "nickname": "길동",
    "deptName": "컴퓨터공학과",
    "studentNum": 26
  },
  "error": null,
  "traceId": "trace-id"
}
```

`GET /api/v1/users/profile`의 `data.profile`에는 학번을 별도로 넣지 않는다. 마이페이지의 학번은 `/users/me`의 `data.studentNum`을 사용한다.

## 4. 1:1 매칭

### 4.1 추천 프로필

```http
GET /api/v1/matching/recommendations
GET /api/v1/matching/recommendations/same-gender
```

필드 위치:

```text
data.candidates[].studentNum
```

```json
{
  "success": true,
  "data": {
    "count": 1,
    "candidates": [
      {
        "userId": 123,
        "nickname": "하늘",
        "deptName": "항공관광학과",
        "studentNum": 26,
        "emailVerified": true
      }
    ]
  },
  "error": null,
  "traceId": "trace-id"
}
```

`candidates[].admissionYear`는 반환하지 않는다.

### 4.2 받은 신청·보낸 신청

```http
GET /api/v1/matching/requests
```

필드 위치:

```text
data.received[].studentNum
data.sent[].studentNum
```

```json
{
  "success": true,
  "data": {
    "sentCount": 1,
    "receivedCount": 1,
    "sent": [
      {
        "connectionId": 501,
        "userId": 123,
        "nickname": "하늘",
        "studentNum": 26,
        "status": "PENDING"
      }
    ],
    "received": [
      {
        "connectionId": 502,
        "userId": 124,
        "nickname": "바다",
        "studentNum": 24,
        "status": "PENDING"
      }
    ]
  },
  "error": null,
  "traceId": "trace-id"
}
```

받은 신청과 보낸 신청 모두 `admissionYear`는 반환하지 않는다.

## 5. 그룹 매칭 멤버 상세

```http
GET /api/v1/matching/team-rooms/{teamRoomId}/members/{targetUserId}/profile
```

필드 위치:

```text
studentNum
```

이 API는 공통 `data` 래퍼 없이 응답 객체를 바로 반환한다.

```json
{
  "userId": 123,
  "nickname": "하늘",
  "deptName": "항공관광학과",
  "studentNum": 26,
  "emailVerified": true
}
```

`admissionYear`는 반환하지 않는다.

그룹 대기방의 `members[]` 요약 응답에는 현재 학번 필드가 없으므로, 멤버의 학번이 필요한 경우 위 상세 API를 호출한다.

## 6. 채팅 프로필

### 6.1 1:1 채팅 상대 상세

```http
GET /api/v1/chat/rooms/{roomId}/counterpart-profile
```

필드 위치:

```text
data.studentNum
```

### 6.2 채팅방 전체 참여자 상세

```http
GET /api/v1/chat/rooms/{roomId}/participants/profiles
```

필드 위치:

```text
data[].studentNum
```

### 6.3 선택한 채팅 참여자 프로필

```http
GET /api/v1/chat/rooms/{roomId}/participants/{targetUserId}/profile
```

필드 위치:

```text
data.studentNum
```

채팅 참여자 프로필 예시:

```json
{
  "success": true,
  "data": {
    "userId": 123,
    "nickname": "하늘",
    "deptName": "항공관광학과",
    "studentNum": 26,
    "emailVerified": true
  },
  "error": null,
  "traceId": "trace-id"
}
```

세 API 모두 참여자 상세 객체에서 `admissionYear`는 반환하지 않는다.

### 6.4 채팅방 목록의 예외 필드

```http
GET /api/v1/chat/rooms
```

채팅방 목록의 상대 요약에는 기존 호환 필드인 다음 값이 유지된다.

```text
data[].targetAdmissionYear
```

상세 프로필을 함께 사용하는 앱은 `data[].targetProfile.studentNum`을 우선 사용한다.

## 7. 앱 모델 적용

Kotlin 예시:

```kotlin
data class UserSummary(
    val userId: Long,
    val nickname: String?,
    val studentNum: Int?
)

fun UserSummary.studentNumText(): String? =
    studentNum?.let { "%02d학번".format(it) }
```

Swift 예시:

```swift
struct UserSummary: Decodable {
    let userId: Int64
    let nickname: String?
    let studentNum: Int?
}

func studentNumText(_ value: Int?) -> String? {
    value.map { String(format: "%02d학번", $0) }
}
```

앱 변경 사항:

1. 프로필 상세 모델에서 `admissionYear`를 제거한다.
2. 추천, 받은 신청, 보낸 신청, 그룹 멤버, 채팅 참여자 모두 `studentNum`을 사용한다.
3. 화면에는 두 자리로 맞춰 `26학번`, `00학번`처럼 표시한다.
4. `studentNum`이 `null`이면 학번 영역을 숨긴다.

