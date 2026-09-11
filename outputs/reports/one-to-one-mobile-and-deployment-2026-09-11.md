# 1:1 매칭 변경 명세 — Android / iOS 공통

2026-09-11 · 백엔드 구현 기준. 앱 구현 및 실제 배포 여부는 별도 확인 대상입니다.

## 1. 상대방 정보

추천 후보와 보낸/받은 요청 목록에서 전체 학번(studentNum)을 제거하고 admissionYear를 제공합니다.

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| admissionYear | Int? | 입학 연도. 2024 → 화면에서 24학번. 식별 불가능한 형식이면 null |
| onboardingStatus | String | 온보딩 상태. 기존 필드 유지 |
| emailVerified | Boolean | 학교 이메일 인증 여부 |
| profileExists | Boolean | 프로필 존재 여부 |
| profileImageUploaded | Boolean | 현재 프로필 이미지가 등록되어 있는지 |
| profile.instagram | String? | 기존처럼 매칭 전에도 제공 |

socialId, 상대방 tickets, 계정 상태(status/userStatus)는 제공하지 않습니다.
요청 항목의 status는 **계정 상태가 아니라 매칭 요청 상태**이므로 유지합니다.
응답의 userTicketsRemaining은 **로그인한 본인 잔액**이며 유지합니다.
닉네임, 학과, 나이, 성별, 사진, 키, MBTI, 흡연, 군 복무, 종교, 거주지, 소개 등 기존 프로필 정보는 유지합니다.

DB에 보관된 원래 학번과 회원가입 입력은 변경하지 않습니다. 추천/요청 응답에서만 변환합니다.
같은 MatchingCandidateResponse를 사용하는 그룹매칭의 후보/멤버 응답에도 이 변경이 적용됩니다.
기존 채팅 상세 DTO 및 내 정보 DTO는 이번 변경 대상이 아닙니다.

Android 표시 예: admissionYear?.let { "%02d학번".format(it % 100) }
iOS 표시 예: admissionYear.map { String(format: "%02d학번", $0 % 100) }
null이면 학번 표시를 생략합니다.

## 2. Idempotency-Key

아래 세 API에 1~100자 요청 키를 반드시 넣습니다. UUID 권장.

- GET /api/v1/matching/recommendations
- GET /api/v1/matching/recommendations/same-gender
- POST /api/v1/matching/connect/{targetUserId}

한 번의 사용자 동작에서 키를 만들고, 타임아웃/네트워크 오류 재시도에는 같은 키를 재사용합니다.
별도 추천 새로고침이나 새로운 요청 전송에는 새 키를 만듭니다.
같은 키를 다른 상대 또는 다른 추천 성별 모드에 재사용하면 409 IDEMPOTENCY_KEY_REUSED입니다.
누락/공백 키는 400 IDEMPOTENCY_KEY_REQUIRED입니다.
백엔드의 required=false 바인딩은 서비스가 일관된 오류 JSON을 반환하기 위한 것으로, 키를 생략해도 된다는 의미가 아닙니다.
키 기록의 보관 기간은 기본 30일입니다. 앱은 오래된 작업을 무기한 자동 재시도하지 않습니다.

추천 결과의 userTicketsRemaining과 요청 전송 결과의 userTicketsRemaining은 응답 당시 본인의 잔액입니다.

## 3. 요청 전송

POST /api/v1/matching/connect/{targetUserId}

data 예:

```json
{
  "connectionId": 123,
  "chatRoomId": null,
  "targetUserId": 456,
  "alreadyConnected": false,
  "userTicketsRemaining": 8
}
```

기존 활성 채팅이 있으면 alreadyConnected=true와 chatRoomId를 반환하며 추가 차감하지 않습니다.
별도 키로 이미 대기 중인 상대에게 요청하면 ALREADY_CONNECTED입니다.
같은 키의 재시도는 원래 전송 결과이며 현재 상태 조회를 대체하지 않습니다. 이후 화면 진입 시 요청 목록/채팅 목록을 새로 조회합니다.

## 4. 수락·거절·취소

| 동작 | API | 정상 완료 상태 |
| --- | --- | --- |
| 수락 | POST /api/v1/matching/accept/{connectionId} | ACCEPTED |
| 거절 | POST /api/v1/matching/reject/{connectionId} | REJECTED |
| 요청 취소 | DELETE /api/v1/matching/requests/{connectionId} | CANCELLED |

공통 data:

```json
{
  "connectionId": 123,
  "targetUserId": 456,
  "chatRoomId": null,
  "status": "CANCELLED"
}
```

수락·거절은 받은 사람만, 취소는 보낸 사람만 가능합니다.
동일 수락·동일 거절·동일 취소 재시도는 기존 결과를 반환합니다. 채팅방/알림을 중복 생성하지 않습니다.
이미 거절한 요청을 수락하거나 수락한 요청을 거절하는 등 반대 동작은 INVALID_REQUEST입니다.
재시도도 계정 자격·권한 검사를 우회하지 않으며, 예전 요청 ID는 새 요청을 변경하지 않습니다.

## 5. 만료와 제재

요청 생성 후 기본 7일이 지나면 EXPIRED입니다. 정리 작업 실행 전이라도 수락·거절·취소 시점에 검사합니다.
7일이 지난 요청에 위 API를 호출하면 **HTTP 200 + data.status=EXPIRED**를 반환하며 만료를 DB에 저장합니다.
따라서 HTTP 200만 보고 채팅으로 이동하지 말고 반드시 data.status를 검사합니다.

- ACCEPTED: 유효한 chatRoomId로 이동. 채팅 접근 불가 시 목록 새로고침
- REJECTED / CANCELLED: 해당 대기 카드 제거
- EXPIRED: 카드 제거 후 “응답 기한이 지난 요청이에요” 표시
- 오류: 오류 코드에 맞게 처리하고 최신 목록 조회

GET /api/v1/matching/requests는 유효한 PENDING 목록만 반환합니다.
계정 정지/매칭 제한, 차단, 탈퇴로 종료된 대기 요청은 CANCELLED로 저장되며 제한을 풀어도 복원되지 않습니다.
취소·만료·제재 종료에 대한 티켓 자동 환불은 없습니다.

## 6. 알림

매칭 요청·수락·거절 시 알림 발송 기록이 매칭 변경과 함께 DB에 저장됩니다.
별도 작업이 알림함 원본 및 기기별 Push Outbox를 같은 새 트랜잭션으로 저장합니다.
실패하면 발송 기록이 남아 서버 재시작 후에도 재시도됩니다. 재시도 간격은 5초부터 최대 5분입니다.
이것은 서버 저장/재시도 보장입니다. OS/FCM의 실제 기기 표시까지 보장하는 것은 아닙니다.
기존 알림 유형과 딥링크 형식은 유지합니다.

## 7. 앱 호환성 및 배포 순서

1. Android/iOS 응답 모델에서 studentNum 의존성을 제거하고 admissionYear를 nullable로 추가합니다.
2. socialId, 상대방 tickets, 계정 status/userStatus를 필수 파싱 항목에서 제거합니다.
3. onboardingStatus, emailVerified, profileExists, profileImageUploaded는 유지합니다.
4. Idempotency-Key 생성·보관·재시도 동작을 확인합니다.
5. CANCELLED/EXPIRED와 HTTP 200 EXPIRED 분기를 구현합니다.
6. 구 백엔드와 공존하는 앱 버전은 admissionYear가 없으면 학번 표시를 생략합니다.
7. 앱 계약 반영 후 백엔드 SQL 마이그레이션 및 배포를 진행합니다.

이 저장소에는 Android/iOS 앱 소스가 없으므로 앱 빌드, 실기기 테스트, 앱스토어 배포 완료를 확인하지 않았습니다.

앱 인수 테스트: 응답 유실 후 수락 재시도, 연속 탭, 만료 요청 수락, 본인/타인 취소 권한, 제재 후 목록 새로고침,
Instagram 유지, 20240001이 API 응답에 없고 admissionYear=2024로 표시되는지 확인합니다.

## 8. 서버 적용

아래 명령은 변경 코드가 원격 develop에 반영되고 앱 호환성 확인이 끝난 후 PuTTY에서 실행합니다.
각 단계 실패 시 중단합니다. 마이그레이션 중 애플리케이션은 잠시 정지합니다.
MySQL의 기존 ENUM/VARCHAR 상태 컬럼을 VARCHAR(32)로 통일하고 알림 이벤트 테이블을 생성합니다.
기존 사용자 학번 원본은 변경하지 않습니다.

```bash
(
set -e
cd ~/AirConnect
git fetch origin
git checkout develop
git pull --ff-only origin develop
docker compose --env-file .env -f docker-compose.yml build airconnect-app
docker compose --env-file .env -f docker-compose.yml stop airconnect-app
umask 077
docker compose --env-file .env -f docker-compose.yml exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "backup-one-to-one-$(date +%Y%m%d-%H%M%S).sql"
docker compose --env-file .env -f docker-compose.yml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < src/main/resources/sql/one_to_one_matching_safety_migration_mysql.sql
docker compose --env-file .env -f docker-compose.yml up -d --no-deps airconnect-app
docker compose --env-file .env -f docker-compose.yml ps
curl --retry 30 --retry-delay 2 --retry-connrefused --retry-all-errors --max-time 5 \
  -fsS http://127.0.0.1:8080/api/v1/maintenance
echo
)
```

기동 API 성공은 서버 기동 확인입니다. 별도 테스트 계정으로 요청 → 수락 → 채팅방 확인과 알림 생성까지 점검해야 합니다.
실패 시 재시작을 반복하기 전에 아래 로그를 확인합니다.

```bash
cd ~/AirConnect
docker compose --env-file .env -f docker-compose.yml logs --no-color --tail=200 airconnect-app
```
