# 관리자 개선 1단계: 배포 누락 정리

확인일: 2026-09-06. 운영 서버, DB, Redis에 접속하거나 변경하지 않았다.
이번 단계는 기존 변경의 의존성 확인과 배포 전 검증이다. 대화 열람 조건,
데이터 점검 판정, 운영 복구 기능은 다음 단계이며 이번에 바꾸지 않았다.

## 기준과 결론

- 백엔드 브랜치: `codex/block-group-direct-join`
- 조사 시작 시 백엔드 HEAD: `a87e094f33a55a2f88951b47f7bf2c76393f2017`
- 관리자 프론트 HEAD: `7d4bafc` (소스 미변경)
- 조사 시작 시 HEAD에는 아래 기능이 빠져 있어 새 관리자 화면의 배포 후보로 사용할 수 없었다.
- 아래 기능과 의존 파일을 함께 커밋 대상으로 정리했다. 실제 후보 SHA는 `git rev-parse HEAD`와 커밋 확인 장치로 확인한다.
- 로컬 테스트 통과는 운영 반영 증거가 아니다. 운영 SHA와 DB 적용 여부는 미확인이다.

## 함께 반영해야 하는 기능 묶음

| 묶음 | 핵심 파일/의존성 | 프론트 계약 |
|---|---|---|
| 대화 열람·기본 응답 분리 | AdminController, AdminService, AdminRequests, AdminDtos, ChatMessageRepository, AdminAuditLogService/Repository/Action, AdminChatInspectionRetentionWorker | POST `/api/v1/admin/chat-rooms/{roomId}/message-inspections`; 목록·기본 상세는 본문 없음 |
| 그룹 진행·남녀 대기열 | AdminGroupMatchingController/Service/Repository/Dtos, AdminGroupQueueObserver, GMatchResultRepository | GET `/api/v1/admin/group-matching/teams`, `/waiting`, `/{teamId}` |
| 신고 상세·처리 | AdminReportController/Service, UserReport/Repository, AdminAuditLog 및 reportId 연결, AdminRequests/Dtos | GET/PATCH `/api/v1/admin/reports/{reportId}`, POST `/{reportId}/evidence-inspections`; expectedVersion, internalMemo, reporterReply |
| 수동 티켓 중복 방지 | AdminTicketAdjustment/Controller/Service/Repository, AdminRequests/Dtos; 기존 AdminController 조정 핸들러 제거까지 포함 | POST `/api/v1/admin/tickets/adjustments`, GET `/{operationId}`; operationId 요청 및 처리 결과 |
| 티켓 변경 일관성 | UserTicketLockRepository/Impl, UserRepository, User의 부분 갱신, TicketLedger/LedgerRefType, 구매·환불·광고·매칭·그룹매칭·보상 지급 경로 | 동일 잔액에 접근하는 모든 변경 경로를 함께 검증 |
| 점검 문구/상태 분리 | MaintenanceAdminController, MaintenanceService/Setting, Content/State 요청 DTO, StatusResponse, 기존 UpdateRequest 제거 | PATCH `/api/v1/admin/maintenance/content`, `/state`; expectedVersion 요청, version 응답; 기존 통합 쓰기 경로 제거 |
| Phase 1/2/3 보안 | ChatRoomController, ChatService, GMatchingService와 관련 테스트 | GROUP 외부 join 금지, 현재 상태 기준 SEND 차단, Redis 토큰 원자 해제 |

위 묶음은 이전에 작성된 미커밋 변경이다. 이번 단계에서 업무 정책을 새로 구현한 것이 아니다.
특히 티켓 새 DTO만 반영하거나 새 컨트롤러만 반영하면 기존 핸들러 충돌/계약 불일치가 생길 수 있다.
프론트 기본 GET이 성공해도 변경 API의 버전·작업번호 지원이 보장되지는 않는다.

배포 범위에서 제외: FigmaSetup.exe, design-system-state-airconnect-16-screens.json,
tools/airconnect-figma-builder 및 운영 설정·비밀키·로컬 산출물. 삭제하거나 변경하지 않았다.

## DB 선행 조건 (격리 MySQL 검증 완료, 운영 미적용)

모든 경로는 `src/main/resources/sql/` 아래다. SQL은 애플리케이션 실행 시 자동 적용되지 않는다.

| 파일 | 확인할 내용 |
|---|---|
| maintenance_version_migration_mysql.sql | maintenance_settings.version BIGINT NOT NULL, 기존 행 0 |
| admin_ticket_adjustments_migration_mysql.sql | 작업 결과 테이블, operation_id UNIQUE, InnoDB; 기존 작업 기록은 추측 생성하지 않음 |
| admin_report_handling_migration_mysql.sql | user_reports의 version·내부 메모·외부 답변·처리자·완료 시각; admin_audit_logs.report_id 및 인덱스 |
| ticket_history_reference_types_mysql.sql | ticket_ledger.ref_type 실제 VARCHAR/ENUM 확인. 새 GROUP_MATCHING/MILESTONE_REWARD 허용; 실제 ENUM의 기존 값을 보존 |

보고서 마이그레이션은 기존 종결 신고의 실제 updated_at을 completed_at에 복사한다.
위 DDL은 무조건 재실행 가능한 스크립트가 아니다. 적용 이력과 실제 열/인덱스 존재 여부를
확인한 후 미적용 부분만 검토해야 한다. ENUM 예시는 주석이며 그대로 자동 실행하지 않는다.
운영 스키마 백업·변경 영향 확인 없이 실행하지 않는다. 버전 필드 없는 구버전 앱으로
롤백해 계속 쓰기 작업을 하면 동시성 계약이 달라지므로, 롤백도 관리자 쓰기 중단과 함께 검토한다.

## 이번 단계 추가 검증 장치

1. `AdminReleaseContractTest`: 관리자 컨트롤러 5개를 함께 등록한다. 경로 중복은 등록 실패로,
   필수 14개 경로 누락은 검증 실패로 감지한다. 티켓 작업번호, 신고 버전/메모/답변,
   점검 문구와 상태의 분리, 기본 채팅 응답 본문 제외도 확인한다.
   서비스는 mock이며 실제 권한·DB·배포 상태 검증을 대신하지 않는다.
2. `tools/isolated-admin-tests.init.gradle`의 `-PadminReleaseVerification` 옵션:
   `build/admin-release-verification`에 별도로 컴파일한다. application 설정과 key 리소스를
   제외하고 Spring Boot의 기본 설정 탐색을 차단한다.
3. `tools/admin-release-check.ps1`: 읽기 전용 커밋 비교 장치. 필수 파일과 배포 범위 변경이
   커밋에서 빠졌거나 다른 경우 exit 1. 파일 내용·비밀 설정은 출력하지 않는다.
   승인된 CI/배포에 아직 자동 연결하지 않았으므로 실행하지 않으면 배포를 막아 주지는 않는다.

```powershell
# 소스 존재 여부만 확인 (배포 승인 아님)
./tools/admin-release-check.ps1 -WorkingTreeOnly
# 테스트할/배포할 커밋이 검토한 작업 트리와 일치하는지 확인
./tools/admin-release-check.ps1 -CommitRef HEAD
```

## 실행한 검증

새 출력 폴더에서 설정을 제외한 선택 회귀 테스트와 bootJar를 실행했다.
전체 프로젝트의 모든 테스트를 실행했다는 뜻은 아니다.

| 범위 | 통과 | 건너뜀 | 실패 |
|---|---:|---:|---:|
| 관리자 (신규 계약 5개, 실제 MySQL HTTP 통합 20개 포함) | 265 | 0 | 0 |
| 인증 AuthServiceTest | 11 | 0 | 0 |
| Chat (Phase 1: 6, Phase 2: 17 포함) | 57 | 0 | 0 |
| STOMP | 6 | 0 | 0 |
| GroupMatching (process lock: 11 포함) | 39 | 0 | 0 |
| IAP application | 22 | 0 | 0 |
| Maintenance | 26 | 0 | 0 |
| Matching race | 2 | 0 | 0 |
| 회원 티켓 동시성·보상·프로필 이미지 | 27 | 0 | 0 |
| VerificationServiceTest | 12 | 0 | 0 |
| **백엔드 합계** | **467** | **0** | **0** |
| 관리자 프론트 | 138 | 0 | 0 |

- 프론트 타입 검사: 통과 (`tsc --noEmit --incremental false`).
- 프론트 Vite 빌드: 통과. JS 554.91 kB로 500 kB 청크 경고 있음. 이번 범위 밖이므로 분할 변경하지 않음.
- 검증 JAR: 필수 컨트롤러·열람 기록 정리 worker와 SQL 4개 포함 확인.
  application 설정/key 및 IsolatedReportMySqlServer/신규 테스트 클래스가 들어 있지 않음 확인.
- 이 JAR는 설정을 제외한 검증용이다. 그대로 운영 배포했다거나 운영 기동이 검증됐다고 보지 않는다.
- 커밋 확인 장치: WorkingTreeOnly 성공; HEAD는 누락/차이로 exit 1 (의도한 차단);
  옵션처럼 보이는 잘못된 CommitRef도 exit 1.
- 실제 MySQL 8.0.43/InnoDB에서 합성 이전 스키마 → 신고/점검/티켓 작업 테이블 SQL → Hibernate validate를 확인했다. Hibernate update로 누락 열을 자동 보충하지 않았다.
- `ticket_ledger.ref_type`은 VARCHAR(30) 사례를 확인했다. 운영 native ENUM 값 보존 사례는 별도 확인이 필요하다.
- 같은 작업번호 순차·동시 티켓 조정은 잔액·변경 기록·처리 결과가 한 번만 반영됐다. 기록 저장 실패 주입 시 모두 롤백됐다.
- 오래된 점검 문구 저장은 409로 거절되고 최신 점검 상태가 유지됐다. 같은 버전의 동시 상태 변경은 200/409로 한 건만 반영됐다.
- 신고 동시 처리, 내부 메모/외부 답변 분리, 감사 실패 시 롤백, 비관리자 및 제재 사용자 접근 차단을 실제 HTTP/MySQL에서 확인했다.
- 브라우저 → localhost 프록시 → 실제 MVC/서비스 → 격리 MySQL: 합성 관리자 로그인, 2:2·3:3 빈 남녀 대기열, 방 목록·기본 상세, 사유 확인 후 대화 열람, 삭제 원문 표시, 메시지 상세, 로그아웃을 확인했다.
- 합성 `<script>` 문자열이 그대로 표시됐고 브라우저 경고창은 실행되지 않았다. 본문 조회 시 열람 기록 저장 완료가 표시됐다.
- 브라우저 검증은 모든 화면 통합 검증이 아니다. 운영 홈 통계 등 범위 밖 서비스는 mock이라 응답 형식 오류가 표시된다. 운영 장애의 증거로 해석하지 않는다.
- 검증용 Vite는 운영 연결을 localhost로 치환하고 네트워크를 CSP로 제한한다. React 개발 preamble을 위한 inline script 허용은 이 도구에만 적용되며 제품 설정은 바꾸지 않았다.

### 백엔드 재현 명령

JDK 17, Gradle wrapper 사용. Windows에서는 `./gradlew` 대신 `./gradlew.bat`.

```sh
./gradlew -I tools/report-mysql-verification.init.gradle -PadminReleaseVerification test \
  --tests 'univ.airconnect.admin.*' --tests 'univ.airconnect.chat.*' \
  --tests 'univ.airconnect.groupmatching.*' --tests 'univ.airconnect.maintenance.*' \
  --tests 'univ.airconnect.iap.application.*' \
  --tests 'univ.airconnect.user.repository.UserTicketBalanceConcurrencyTest' \
  --tests 'univ.airconnect.user.service.MilestoneTicketConsistencyTest' \
  --tests 'univ.airconnect.matching.service.MatchingServiceRaceTest' \
  --tests 'univ.airconnect.user.service.UserProfileImageServiceTest' \
  --tests 'univ.airconnect.verification.service.VerificationServiceTest' \
  --tests 'univ.airconnect.auth.service.AuthServiceTest' \
  --tests 'univ.airconnect.global.security.stomp.*' bootJar --console=plain
```

### 격리 MySQL 준비 조건

테스트 서버는 **로컬 127.0.0.1:13389 / airconnect_admin_verify**로 고정돼 있다.
해당 DB는 실행할 때 합성 자료로 재생성하므로 실제 자료가 들어 있는 DB에 사용하지 않는다.
이번 실행은 `airconnect-admin-release-mysql-20260906`이라는 전용 Docker 컨테이너의 tmpfs 저장소를 사용했다.
컨테이너는 loopback에만 노출하고 다른 기존 컨테이너는 변경하지 않았다.
DB 실패 주입용 트리거 생성에는 이 일회용 MySQL에 한해 `log_bin_trust_function_creators=1`이 필요했다.
이 설정은 운영 DB 권장 설정이 아니다. DB가 없는 일반 회귀 실행은 `isolated-admin-tests.init.gradle`을 사용하며 MySQL 테스트는 건너뛴다.

## 아직 배포 승인으로 볼 수 없는 부분

- 실제 MySQL 검증은 합성 스키마 기준이며 운영 DB의 현재 열/인덱스/ENUM 및 마이그레이션 적용 이력은 미확인이다.
- 신고/수동 티켓/점검 동시성은 실제 MySQL에서 확인했다. 다른 동시 잔액 변경 경로 전체를 MySQL로 검증한 것은 아니다.
- Redis process lock 테스트는 가상 시계/직렬화/mock 검증이다. 실제 Redis 원자성 테스트는 미실행.
- 운영 API/스키마와 실제 배포 SHA 확인은 미실행. Redis/FCM 등 외부 의존성은 이 검증에서 mock이다.
- 대화 말풍선 시간과 오른쪽 상세 시간의 표시가 격리 화면에서 달랐다. 시간대 표시 일관성은 다음 UI 단계에서 확인해야 하며 이번 배포 누락 정리에 포함하지 않았다.
- 기존 CI는 develop의 push/PR에만 실행된다. 현재 브랜치에 push했다는 사실만으로
  CI가 실행·통과했다고 볼 수 없다.
- 운영 배포·운영 DB 변경·merge는 수행하지 않았다. 커밋/푸시 완료 여부는 실제 Git 결과로 별도 보고한다.

## 다음 반영 절차

1. 위 기능과 의존 파일, SQL, 테스트, 검증 도구를 선별해 하나의 완전한 배포 후보 커밋으로 정리.
   관련 없는 Figma 산출물과 설정 파일은 포함하지 않는다.
2. 후보 커밋 확인 장치 통과 후 그 SHA의 깨끗한 체크아웃에서 동일 테스트/빌드 재실행.
3. 격리 MySQL 검증 결과와 브라우저 확인 결과를 후보 SHA와 함께 보관.
4. 운영 SHA·스키마 확인 및 백업 후 필요한 DB 변경과 백엔드 배포를 별도 승인 하에 수행.
5. 배포된 버전과 실제 관리자 API 계약을 확인. 로그인 성공만으로 완료 처리하지 않는다.
   운영에서 회원 제재·티켓 지급·공지 발송을 시험 삼아 실행하지 않는다.
6. 여기까지 끝낸 뒤 다음 단계인 대화 조회 간소화를 진행한다.

판정: 로컬 코드·격리 통합 검증은 **PASS**. 운영 반영 판정은 **NEEDS_ADJUSTMENT** (운영 스키마·배포 SHA·API 계약 확인 필요).
