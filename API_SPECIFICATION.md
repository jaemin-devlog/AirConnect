# 🚀 AirConnect API 명세서

**API 버전:** v1  
**기본 URL:** `http://localhost:8080` (로컬)  
**작성일:** 2026-03-13

---

## 📑 목차

- [인증 (Authentication)](#인증-authentication)
- [사용자 (User)](#사용자-user)
- [프로필 (Profile)](#프로필-profile)
- [에러 응답](#에러-응답)

---

## 인증 (Authentication)

### 1. 소셜 로그인
**엔드포인트:** `POST /api/v1/auth/social/login`

**설명:** Apple 또는 Kakao 소셜 로그인을 통해 사용자 인증 및 토큰 발급

**권한:** 없음 (Public)

**요청 본문:**
```json
{
  "provider": "APPLE",
  "socialToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExIn0...",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**요청 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| provider | String | ✅ | `APPLE`, `KAKAO` |
| socialToken | String | ✅ | 소셜 플랫폼의 ID 토큰 |
| deviceId | String | ✅ | 클라이언트 기기 고유 식별자 |

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

**에러:**

| 코드 | Status | 설명 |
|------|--------|------|
| INVALID_LOGIN_REQUEST | 400 | 요청 데이터 검증 실패 |
| SOCIAL_PROVIDER_REQUIRED | 400 | provider 필드 누락 |
| SOCIAL_TOKEN_REQUIRED | 400 | socialToken 필드 누락 |
| DEVICE_ID_REQUIRED | 400 | deviceId 필드 누락 |
| USER_DELETED | 403 | 탈퇴한 사용자 |
| USER_SUSPENDED | 403 | 정지된 사용자 |

---

### 2. 토큰 갱신
**엔드포인트:** `POST /api/v1/auth/refresh`

**설명:** 만료된 액세스 토큰을 새로운 토큰으로 갱신

**권한:** 없음 (Public)

**요청 본문:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

**에러:**

| 코드 | Status | 설명 |
|------|--------|------|
| DEVICE_MISMATCH | 403 | deviceId 불일치 |
| REFRESH_TOKEN_NOT_FOUND | 404 | 저장된 토큰 없음 |
| REFRESH_TOKEN_MISMATCH | 403 | 토큰 값 불일치 |
| USER_NOT_FOUND | 404 | 사용자 없음 |

---

### 3. 로그아웃
**엔드포인트:** `POST /api/v1/auth/logout`

**설명:** 현재 디바이스의 리프레시 토큰 삭제

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**요청 본문:**
```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**성공 응답 (204 No Content):**
```
(응답 본문 없음)
```

---

### 4. 테스트 토큰 생성 (개발용)
**엔드포인트:** `POST /api/v1/auth/test/token`

**설명:** 개발/테스트 환경에서만 임시 토큰 생성

**권한:** 없음 (Public)

**환경:** `dev`, `local` 프로필에서만 사용 가능

**요청 본문:**
```json
{
  "deviceId": "test-device-001"
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

---

## 사용자 (User)

### 1. 회원가입 완료
**엔드포인트:** `POST /api/v1/users/sign-up`

**설명:** 소셜 로그인 후 기본 정보 입력하여 회원가입 완료

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**요청 본문:**
```json
{
  "name": "홍길동",
  "nickname": "길동이",
  "studentNum": 20230001,
  "deptName": "컴퓨터공학과"
}
```

**요청 파라미터:**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| name | String | ✅ | 사용자 실명 (1-100자) |
| nickname | String | ✅ | 닉네임 (1-100자) |
| studentNum | Integer | ✅ | 학번 |
| deptName | String | ✅ | 학과명 (1-100자) |

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "email": "user@example.com",
    "name": "홍길동",
    "status": "ACTIVE"
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

**에러:**

| 코드 | Status | 설명 |
|------|--------|------|
| INVALID_INPUT | 400 | 필수 항목 누락 |
| USER_NOT_FOUND | 404 | 사용자 없음 |
| USER_DELETED | 403 | 탈퇴한 사용자 |
| UNAUTHORIZED | 401 | 유효하지 않은 토큰 |

---

### 2. 내 정보 조회
**엔드포인트:** `GET /api/v1/users/me`

**설명:** 현재 로그인한 사용자의 기본 정보 및 프로필 요약 조회

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "provider": "KAKAO",
    "socialId": "123456789",
    "email": "user@example.com",
    "name": "홍길동",
    "deptName": "컴퓨터공학과",
    "nickname": "길동이",
    "studentNum": 20230001,
    "status": "ACTIVE",
    "onboardingStatus": "FULL",
    "profileExists": true,
    "profile": {
      "userId": 10,
      "nickname": "길동이",
      "gender": "M",
      "department": "컴퓨터공학과",
      "birthYear": 2000,
      "height": 180,
      "mbti": "ENFP",
      "smoking": "N",
      "military": "N",
      "religion": "기독교",
      "residence": "서울시 강남구",
      "intro": "안녕하세요!",
      "contactStyle": "카톡",
      "profileImageKey": "image_key_123",
      "updatedAt": "2026-03-13T14:30:00"
    }
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

**에러:**

| 코드 | Status | 설명 |
|------|--------|------|
| USER_NOT_FOUND | 404 | 사용자 없음 |
| USER_DELETED | 403 | 탈퇴한 사용자 |
| UNAUTHORIZED | 401 | 유효하지 않은 토큰 |

---

### 3. 회원 탈퇴
**엔드포인트:** `DELETE /api/v1/users/me`

**설명:** 현재 계정 탈퇴 (상태 변경, 프로필 익명화, 토큰 삭제)

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**요청 본문 (선택사항):**
```json
{
  "reason": "서비스를 더 이상 사용하지 않음"
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": null,
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

**탈퇴 후 처리:**
- 사용자 상태: `DELETED`로 변경
- 프로필 정보: 익명화 (닉네임은 `deleted-user-{userId}`로 변경)
- 모든 리프레시 토큰 삭제

---

## 프로필 (Profile)

### 1. 프로필 생성
**엔드포인트:** `POST /api/v1/users/profile`

**설명:** 사용자의 상세 프로필 정보를 생성

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**요청 본문:**
```json
{
  "nickname": "길동이",
  "gender": "M",
  "department": "컴퓨터공학과",
  "birthYear": 2000,
  "height": 180,
  "mbti": "ENFP",
  "smoking": "N",
  "military": "N",
  "religion": "기독교",
  "residence": "서울시 강남구",
  "intro": "안녕하세요!",
  "contactStyle": "카톡",
  "profileImageKey": "image_key_123"
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "nickname": "길동이",
    "gender": "M",
    "department": "컴퓨터공학과",
    "birthYear": 2000,
    "height": 180,
    "mbti": "ENFP",
    "smoking": "N",
    "military": "N",
    "religion": "기독교",
    "residence": "서울시 강남구",
    "intro": "안녕하세요!",
    "contactStyle": "카톡",
    "profileImageKey": "image_key_123",
    "updatedAt": "2026-03-13T14:30:00"
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

---

### 2. 프로필 조회
**엔드포인트:** `GET /api/v1/users/profile`

**설명:** 현재 사용자의 프로필 정보 조회

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "nickname": "길동이",
    "gender": "M",
    "department": "컴퓨터공학과",
    "birthYear": 2000,
    "height": 180,
    "mbti": "ENFP",
    "smoking": "N",
    "military": "N",
    "religion": "기독교",
    "residence": "서울시 강남구",
    "intro": "안녕하세요!",
    "contactStyle": "카톡",
    "profileImageKey": "image_key_123",
    "updatedAt": "2026-03-13T14:30:00"
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

---

### 3. 프로필 업데이트
**엔드포인트:** `PATCH /api/v1/users/profile`

**설명:** 기존 프로필 정보를 수정 (부분 수정 가능)

**권한:** ✅ 필수 (`Authorization: Bearer {accessToken}`)

**요청 본문:**
```json
{
  "nickname": "길동이 변경됨",
  "intro": "더 좋은 소개글로 변경!",
  "profileImageKey": "new_image_key_456"
}
```

**성공 응답 (200 OK):**
```json
{
  "success": true,
  "data": {
    "userId": 10,
    "nickname": "길동이 변경됨",
    "gender": "M",
    "department": "컴퓨터공학과",
    "birthYear": 2000,
    "height": 180,
    "mbti": "ENFP",
    "smoking": "N",
    "military": "N",
    "religion": "기독교",
    "residence": "서울시 강남구",
    "intro": "더 좋은 소개글로 변경!",
    "contactStyle": "카톡",
    "profileImageKey": "new_image_key_456",
    "updatedAt": "2026-03-13T14:35:00"
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

---

## 에러 응답

### 표준 에러 응답 형식

```json
{
  "success": false,
  "error": {
    "status": 400,
    "code": "INVALID_INPUT",
    "message": "Invalid input provided"
  },
  "traceId": "d9c447aa-3e8e-4331-92a3-66b7eb432bd7"
}
```

### 전체 에러 코드

| 코드 | Status | 설명 |
|------|--------|------|
| UNAUTHORIZED | 401 | 인증 토큰 누락 또는 유효하지 않음 |
| FORBIDDEN | 403 | 접근 권한 없음 |
| USER_NOT_FOUND | 404 | 사용자 없음 |
| USER_DELETED | 403 | 탈퇴한 사용자 |
| USER_SUSPENDED | 403 | 정지된 사용자 |
| INVALID_INPUT | 400 | 유효하지 않은 입력값 |

---

## 주요 정보

### 토큰 유효 기간
- **Access Token:** 30분
- **Refresh Token:** 30일

### 계정 상태
- **ACTIVE:** 정상 활성 계정
- **SUSPENDED:** 정지된 계정 (로그인 불가)
- **DELETED:** 탈퇴한 계정 (복구 불가)

### 온보딩 상태
- **BASIC:** 소셜 로그인만 완료 (회원가입 미완료)
- **FULL:** 회원가입 완료 (기본 정보 입력 완료)

---

**API 문서 버전:** 1.0.0  
**최종 업데이트:** 2026-03-13  
**담당자:** AirConnect Backend Team

