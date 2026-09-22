# 로컬 미커밋 변경 정리

## 통합 기준

- `git fetch origin --prune` 후 확인한 원격 develop: `10154ab9b260b256d02421f2b73455b926db895a`.
- 기본 폴더는 오래된 `security-hardening@89731ea`이고 사용자 미커밋 변경이 있다. 원본 변경 및 기존 staging 상태를 보존했다.
- 별도 `backend-security-audit` 작업트리에서 최신 develop을 기반으로 필요한 변경만 통합한다. 오래된 파일 전체를 최신 코드 위에 덮어쓰지 않는다.
- Android/iOS/관리자 프론트, 운영 서버, 배포·인프라 설정을 변경하지 않는다.

## 반영 / 제외 판단

| 로컬 항목 | 처리 | 근거 |
|---|---|---|
| 이번 백엔드 보안감사 수정 | 반영 | 인증·인가·입력·로그·보상 취약점 수정 및 회귀 테스트. `SECURITY-AUDIT-2026-09-22.md` 참조. |
| `admission-year-privacy`의 `FestivalCouponConsistencyAuditTest` | 추가 반영 | 서로 다른 사용자의 동시 쿠폰 사용, 같은 사용자 재시도, 트랜잭션 실패 시 쿠폰·잔액·원장 롤백을 더미 H2에서 검증하는 3개 테스트. |
| 같은 작업트리의 이전 JWT/세션/RefreshToken 수정안 | 중복 통합하지 않음 | 이번 보안감사의 고유 jti, 안정적 세션 검사, 원자 회전으로 대응한다. 별도 잠금/30초 이전 토큰 허용 로직은 함께 적용하지 않는다. 시작 시 모든 refresh 값을 재저장하는 backfill은 동시 회전 값을 덮어쓸 수 있으므로 가져오지 않는다. |
| 같은 작업트리의 `JwtIssuanceAuditTest` | 중복 통합하지 않음 | 현재 `JwtProviderTest`에서 동일 초 발급의 고유성과 기존 claim 호환성을 이미 검사한다. |
| 기본 폴더의 공개 초대 링크·연결 파일 | 이미 반영 | develop에 InviteLinkController, SecurityConfig 허용 경로와 테스트가 있다. develop에는 추가 `/download`도 있다. |
| 기본 폴더의 그룹매칭 시작·중지 알림 | 이미 반영 | develop의 `notifyMatchingStarted`/`notifyMatchingStopped`, NotificationType, 수신 설정에 반영되어 있다. |
| 기본 폴더의 Android data-only 채팅 푸시·개별 outbox 처리 | 이미 반영 / 최신 구현 유지 | develop은 해당 동작에 비동기 발송, dispatch token, 채팅방 접속 중 발송 억제 등의 후속 처리가 더해져 있다. 과거 동기 발송 버전으로 되돌리지 않는다. |
| 기본 폴더 `.gitignore`의 디자인 산출물 제외 | 이미 반영 | develop에 동일 패턴이 있다. 별도로 작업트리·캐시·설치 파일·쿠폰 엑셀 제외를 추가한다. |
| 기본 폴더 Compose/application의 앱 링크 설정 | 이미 반영 / 미수정 | develop에 앱 링크 설정이 있다. 로컬 application 설정과 배포 파일을 추가 커밋하지 않는다. |
| 기본 폴더 `INVITE-APP-LINKS.md` | 동일 파일 | 로컬 파일과 origin/develop의 정규화 Git blob이 같다. |
| 쿠폰 원본 엑셀 | 제외·원본 보존 | 사용 가능한 쿠폰 번호가 담긴 배포용 원본이며, 소스 공개 범위를 불필요하게 확대하지 않는다. 원본 파일과 기본 폴더의 기존 staged 상태는 변경하지 않는다. |
| outputs의 과거 명세서·보안 보고서·Word 자료 | 로컬 보존 | 과거 기능/시점의 문서이며 이번 런타임 수정의 필수 소스가 아니다. 최신 API 계약으로 재검증하지 않은 문서를 일괄 게시하지 않는다. |
| `chat-notification-reliability`의 모바일 명세서 수정 | 로컬 보존 | 오래된 작업트리의 문서 변경이다. 모바일 인터페이스 변경 없이 이번 코드를 통합한다. |
| `develop-chat-notification-merge`의 온보딩·랭킹 명세서 | 로컬 보존 | 미추적 과거 문서로서 이번 통합에 필요하지 않다. |
| `android-chat-push-delivery` | 추가 항목 없음 | 미커밋 변경이 없는 작업트리다. |

## 검증 및 주의

- 격리 JDK 17/H2 전체 `test bootJar`: BUILD SUCCESSFUL (1분 36초). JUnit XML 기준 총 949개 항목 중 928개 통과, 실패/오류 0개, 별도 MySQL 환경이 필요한 21개 건너뜀. 추가 쿠폰 테스트 3개 모두 통과했다.
- `git diff --check` 통과. 개발 브랜치 푸시 시 저장소에 정의된 GitHub Actions는 빌드·테스트이며 배포 단계는 없다.
- 실제 Redis/MySQL·모바일 실기기·신구 서버 혼합 배포의 확인 한계는 보안감사 보고서와 같다.
- 원본 작업트리를 삭제하거나 강제 초기화하지 않는다. 따라서 이전 폴더에는 중복·보류 변경이 계속 표시될 수 있다.
- 운영 배포는 이 작업에 포함하지 않는다.
