# AirConnect 백엔드 보안감사·수정

기존 `/docs/` Git 제외 규칙을 변경하지 않기 위해 보고서는 저장소 루트에 둔다.

## 범위와 기준

- 기준: 원격 `develop`의 `10154ab9b260`에서 분리한 `backend-security-audit` 브랜치, 2026-09-22.
- 원래 `security-hardening` 작업 트리의 미커밋 변경은 보존했다. 이 보고서의 수정은 별도 작업 트리에만 있다.
- 백엔드 Java 코드, 요청 DTO·컨트롤러, 권한/저장소 쿼리, 외부 통신, 로그, Maven 런타임 의존성 및 접근 가능한 Git 이력을 점검했다. Docker/Caddy/Compose는 읽기 확인만 했다.
- Android/iOS/관리자 프론트, 운영 서버, 운영 DB, 배포 설정은 수정하지 않았다. 운영 배포는 하지 않았다. 감사 이후 사용자의 후속 요청에 따라 Git 커밋·푸시 대상으로 정리했다.
- 기존 API URL, 요청·응답 필드, STOMP 목적지는 유지했다. 잘못된 입력/과도한 요청/폐기된 인증에는 400·429·503·인증 오류가 추가될 수 있다.
- 실제 운영 컨테이너와 이 기준 커밋의 일치는 확인하지 않았다. 정적 점검과 로컬 더미 테스트이며 운영 침투 테스트나 모든 조합의 전수 검증이 아니다.

## 확인된 문제와 수정

경로는 `src/main/java/univ/airconnect/` 기준이다. 높음은 계정·개인정보·보상에 직접 영향, 중간은 제한된 노출·자원 남용·신뢰 경계 문제다.

| 취약점 | 위험도 | 수정 여부 | 수정 파일 | 수정 내용 |
|---|---|---|---|---|
| 동일 초에 발급된 Refresh JWT가 같아 회전 후에도 이전 토큰 재사용 | 높음 | 수정 | `global/security/jwt/JwtProvider.java` | 매 발급마다 고유 `jti`를 사용한다. 기존 토큰도 검증 가능하다. |
| 동시 refresh 중복 성공, 로그아웃과 경합해 세션 재생성, 늦은 요청이 정상 세션 삭제 | 높음 | 수정 | `auth/service/AuthService.java`, `auth/security/RefreshTokenRotationService.java` | Redis Lua의 비교 후 교체로 단일 승자를 보장한다. 오래된 요청은 새 세션을 삭제하지 않는다. |
| 로그아웃 후 기존 Access Token·소켓 재사용 | 높음 | 수정·구형 토큰 유예 | `auth/security/AccessTokenRevocationService.java`, `auth/domain/entity/RefreshToken.java`, `JwtProvider.java`, `auth/controller/AuthController.java` | 신규 JWT를 기기별 안정적인 서버 세션에 연결한다. 로그아웃/같은 기기 재로그인 시 그 세션의 이전 AT도 거절한다. 로그아웃에 제시한 구형 AT는 지문으로 개별 폐기한다. |
| Redis 사용자 인덱스 누락으로 관리자/탈퇴 시 refresh 일괄 폐기 누락 | 높음 | 수정 | `auth/repository/RefreshTokenRepository.java`, `RefreshTokenUserLookupImpl.java` | 기존 미인덱싱 자료까지 사용자 키 접두사의 SCAN으로 조회한다. 전체 KEYS 명령은 사용하지 않는다. |
| CONNECT 후 JWT 만료·원격 로그아웃을 반영하지 않는 STOMP 세션 | 높음 | 수정 | `global/security/stomp/StompSessionRegistry.java`, `StompHandler.java`, `StompOutboundAuthorizationInterceptor.java` | INBOUND/OUTBOUND에서 만료·지문·서버 세션을 확인한다. 인증을 잃은 수신은 빈 ERROR로 연결 종료와 presence 정리를 유도한다. |
| 차단 후 방 ID/궁합 ID를 통한 상대의 현재 프로필 접근 | 중간 | 수정 | `chat/service/ChatService.java`, `compatibility/service/CompatibilityService.java` | 양방향 차단 검사, 목록의 차단 상대 프로필 정보 숨김, 상세 조회 거절. 대화 자체를 임의 삭제하지 않는다. |
| 차단된 사용자와 초대 팀 가입·그룹 페어링·지연 최종 매칭 가능 | 중간 | 수정 | `groupmatching/service/GMatchingService.java`, `controller/GMatchingController.java`, 관련 entity | 가입/조회/대기/페어링/최종 확정 시 재확인한다. 확정 전 차단 발생 시 결제하지 않고 재시도 가능한 상태로 돌린다. |
| 학교 OTP의 IP 교체 추측·동시 재사용, 인증 완료 토큰 중복 소비 | 높음 | 수정 | `verification/service/VerificationService.java` | 이메일별 오답 예산 5회, 코드 비교와 삭제 원자화, 완료 토큰 GETDEL, 발송 예약 SET NX. |
| 미리 여러 광고 세션을 만든 뒤 하루 보상 한도 초과 | 높음 | 수정 | `ticket/service/AdTicketGrantService.java`, `repository/AdTicketLedgerRepository.java` | 실제 지급 시 사용자 잠금과 당일 원장의 현재 조회로 한도를 재검증한다. |
| 서로 다른 친구의 동시 추천 입력으로 10명 보너스 중복 지급 | 높음 | 수정 | `referral/service/ReferralService.java`, `repository/ReferralRedemptionRepository.java` | MySQL Repeatable Read의 이전 스냅샷 대신 잠금 후 현재 행 조회로 보너스 단계 계산. |
| 관리자 로그인 IP 변경으로 실패 제한 우회 | 중간 | 수정 | `auth/service/AuthService.java` | 기존 IP별 제한 외 계정 전체 20회/5분 예산을 적용한다. 공격에 의한 일시 잠금 가능성은 남는다. |
| 쿠폰·초대코드 추측, 반복 업로드·유료 외부 호출·채팅 자원 남용 | 중간 | 수정 | `global/security/SensitiveApiRateLimitInterceptor.java`, `chat/service/ChatMessageThrottleService.java`, `global/config/WebMvcConfig.java` | 인증 사용자 기준 Redis 원자 카운터. IP 헤더를 바꿔도 사용자 제한은 유지되며 두 배포 슬롯이 공유한다. |
| 예외에 포함된 토큰·결제키·인증메일·푸시/프로필 내용의 로그 노출 | 중간 | 수정 | `GlobalExceptionHandler.java`, `analytics/web/ApiRequestLoggingInterceptor.java`, Apple/Kakao/IAP/SMTP/알림/궁합/UserService | 원문과 provider/parser 오류문을 제외하고 코드·오류 종류·추적 ID 등을 기록한다. 미매칭 URI도 원문 로깅하지 않는다. |
| STOMP ERROR에서 내부 예외문 반환, 작성된 커스텀 handler 미등록 | 중간 | 수정 | `global/security/stomp/CustomStompErrorHandler.java`, `global/config/WebSocketConfig.java` | 정형 오류만 반환하고 endpoint registry에 handler를 실제 연결한다. |
| App Store 서명에 일반 TLS 루트 CA까지 신뢰 | 중간 | 수정 | `iap/apple/AppleSignedTransactionVerifier.java` | 번들된 Apple 루트 인증서만 trust anchor로 사용한다. 실제 Apple WWDR→Root 체인 fixture를 확인했다. |
| IAP 배치/토큰·프로필 문자열 무제한 입력 및 외부 통신 무제한 대기 | 중간 | 수정 | IAP 요청 DTO 4개, `user/dto/request/SignUpRequest.java`, `UpdateProfileRequest.java`, OAuth/Google 클라이언트 | 결제 배치 최대 100개, 토큰/JWS 길이 및 DB 문자열 길이 상한. 외부 연결·읽기 제한시간을 추가한다. 나이/enum 등 비즈니스 정책은 변경하지 않는다. |

## 요청 제한의 실제 값

성공·실패 시도를 함께 센다. 쿠폰 여러 장 등록 기능은 유지하며 총 사용 개수 제한으로 바꾸지 않았다.

| 사용자별 기능 | 분당 | 시간당 |
|---|---:|---:|
| 쿠폰 입력 | 5 | 30 |
| 그룹 초대코드 입력 | 10 | 60 |
| 프로필 이미지 업로드 | 10 | 60 |
| 광고 보상 세션 생성 | 10 | 60 |
| IAP 단건 검증 (플랫폼 공통 예산) | 20 | 120 |
| IAP 동기화 (플랫폼 공통 예산) | 3 | 20 |
| 궁합 조회 | 30 | 300 |
| 채팅 SEND (REST/STOMP 공통) | 120 | 별도 없음 |

일반 REST 제한 초과 시 기존 `ApiResponse` 구조의 429와 `Retry-After`를 반환한다. Redis 장애에서는 보안 검사를 생략하지 않으며 503 또는 인증 실패로 처리한다. 대량 계정 생성이나 네트워크 DDoS 전체를 이 제한만으로 해결하지는 못한다.

## 앱 또는 운영 판단이 필요해 수정하지 않은 항목

| 항목 | 구분 및 남은 위험 |
|---|---|
| 기존 `sid` 없는 Access JWT | 앱 재로그인을 강제하지 않기 위해 만료까지 호환한다. 제시되지 않은 구형 AT의 즉시 기기별 폐기는 보장하지 않는다. 새 로그인/갱신 토큰부터 세션 검사가 적용된다. |
| 신·구 백엔드의 동시 실행 | 구버전 refresh 저장이 신규 `sessionId` 필드를 지우면 새 AT가 조기 거절될 수 있다. 실제 blue/green 전환 및 rollback 검증은 별도로 필요하다. 배포는 하지 않았다. |
| 모바일의 동시 refresh·WebSocket 재연결 | 같은 Refresh Token으로 동시에 갱신하면 한 요청만 성공하며 나머지는 기존 재사용 오류를 받는다. 서버는 승자의 세션을 폐기하지 않지만 앱의 동시 갱신 합치기·실패 처리와 만료 후 STOMP 재연결은 실기기 확인이 필요하다. 앱 코드는 변경하지 않았다. |
| Kakao 토큰의 발급 앱 바인딩 | 현재 `/v2/user/me` 확인만으로 기대 앱 `app_id`와 대조하지 않는다. 올바른 서비스 앱 ID 확인·설정이 필요하다. 추측한 ID로 로그인을 차단하지 않았다. |
| Apple 로그인 nonce/challenge | 현재 모바일 요청과 서버 challenge 연동이 필요하다. 기존 필드를 임의로 필수화하지 않았다. |
| Android 구매 최초 사용자 귀속 | 서버의 기존 주문 소유권은 검사하지만 최초 구매의 `obfuscatedExternalAccountId` 바인딩은 모바일 연동 확인이 필요하다. 정상 결제를 전부 거절하는 변경은 하지 않았다. |
| 공개 프로필 이미지 URL | 현재 앱이 인증 없는 URL로 읽는 구조다. UUID 파일명·경로 검증·탈퇴/정지 숨김은 있지만 이미 URL을 아는 상대에 대한 개별 차단은 보장하지 않는다. 인증 이미지/만료 URL은 앱 계약 확인 필요. |
| 기존 오프라인 쿠폰 500개 | `src/main/resources/festival/coupons-500.txt`가 Git/패키지에 포함된다. 코드가 유출되었다면 미사용 쿠폰은 추측 제한으로 보호되지 않는다. 기존 배포/인쇄 쿠폰을 무효화하지 않았고 원문도 출력하지 않았다. 교체 판단 필요. |
| 탈퇴 자료 보존 | 셀프 탈퇴는 상태 차단·세션 폐기·일부 식별정보 정리이며, 프로필 전체 삭제는 관리자 정리 경로와 구분되어 있다. 전체 보존 기한/분쟁·결제 증빙 정책 없이 데이터를 임의 삭제하지 않았다. |
| 프록시 IP 신뢰·익명 OAuth 대량 요청 | `ClientIpResolver`는 전달 헤더를 사용한다. 사용자/이메일/관리자 계정별 제한은 헤더 회전과 독립적으로 보강했으나, 실제 trusted proxy 및 엣지 제한은 운영 설정 확인 대상이다. |
| 서버 컨테이너 권한·이미지 | Dockerfile의 기본 root 실행과 가변 이미지 태그는 배포 보강 대상이다. Compose의 app/DB/Redis 호스트 바인딩은 loopback이나 실제 방화벽·배포 상태는 검증하지 않았다. 인프라 변경 금지에 따라 미변경. |

## 이미 안전해서 유지한 부분

- JWT 서명·만료·토큰 타입 검사, HTTP 요청의 현재 DB 역할/계정 상태 확인, BCrypt 관리자 비밀번호, Refresh Token 해시 저장.
- 관리자 URL/운영 지표 권한, STOMP 허용 목적지·방 멤버십·개인 목록 사용자 ID 검사.
- 일반 쿼리의 바인딩 파라미터, 서버가 선택하는 고정 정렬식. 요청이 임의 SQL/SpEL/셸 명령으로 직접 실행되는 경로는 이번 점검에서 확인하지 못했다.
- 이미지 크기/픽셀 수/확장자·실제 디코딩 검사, 재인코딩, 서버 생성 파일명, 저장 경로 경계 검사. 서버가 사용자 지정 이미지 URL을 가져오는 경로도 확인하지 못했다.
- 일반 CORS 기본 허용 출처 목록, stateless bearer 인증, Spring Security 기본 보안 헤더. CSRF를 무조건 켜서 모바일 bearer 호출을 깨뜨리는 변경은 하지 않았다.
- 사용자 알림·푸시 이벤트의 소유권 확인, IAP 공급자 서명/OIDC 및 기존 거래 중복·소유권 검사.

## 의존성·Git 확인

- Gradle `runtimeClasspath` 실제 해석 결과 202개 Maven 모듈을 조회했고, 공개 패키지 좌표/버전만 OSV batch API로 대조했다. 이 조회 결과에는 매칭 항목이 없었다. 이는 취약점 부재 보장이 아니다.
- 별도로 공식 공지를 대조했다. 현재 Spring Security 6.5.11의 WebAuthn/DPoP/AES 관련 공지는 버전 범위가 겹치지만 해당 기능 사용은 확인되지 않았다. Spring Data JPA의 비신뢰 Sort+native SQL 조합도 확인되지 않았다. 따라서 Boot 4/Security 7로 광범위하게 전환하지 않았다.
- 확인한 주요 실제 버전: Boot 3.5.16, Framework 6.2.19, Security 6.5.11, Tomcat 10.1.59, Netty 4.1.138.Final, Jackson 2.21.5, JJWT 0.11.5. 기존 Tomcat/Netty 패치가 적용되어 있었다. JJWT CVE-2024-31033은 철회된 공지이므로 확정 취약점으로 세지 않았다.
- 접근 가능한 Git 이력 481개 커밋에서 키 파일 이름과 PEM/토큰/하드코딩 설정 패턴을 검색했다. 이번 검색에서 실제 개인키 유출을 확정하지 않았다. `BEGIN PRIVATE KEY` 일치는 PEM 헤더를 제거하는 파서 코드였다. 이력에 접근할 수 없는 저장소/삭제된 객체와 임의 인코딩된 비밀까지 검증한 것은 아니다.
- 실행한 인벤토리 도구: `tools/security-dependencies.init.gradle`. 로컬 결과는 `build/admin-release-verification/security-audit/runtime-dependencies.json`.

공식 근거: [Spring 공지](https://spring.io/security/), [WebAuthn](https://spring.io/security/cve-2026-47841/), [DPoP](https://spring.io/security/cve-2026-41707/), [AES](https://spring.io/security/cve-2026-47842/), [JPA Sort](https://spring.io/security/cve-2026-47834/), [Tomcat](https://tomcat.apache.org/security-10.html), [Netty](https://netty.io/news/2026/09/09/4-1-138-Final.html), [철회된 JJWT 공지](https://osv.dev/vulnerability/GHSA-r65j-6h5f-4f92), [Apple 루트 인증서](https://github.com/apple/app-store-server-library-java#obtaining-apple-root-certificates), [Kakao 토큰 정보](https://developers.kakao.com/docs/en/kakaologin/rest-api#req-access-token-info).

## 검증

최종 전체 실행 `BUILD SUCCESSFUL` (1분 47초).

- JUnit XML 기준 136개 suite, 총 946개 항목: **925개 통과, 실패/오류 0개, 21개 건너뜀**.
- 건너뜀: 별도 MySQL 환경이 필요한 `AdminIntegrityQueryRepositoryMySqlTest` 1개와 `AdminReportMySqlIntegrationTest` 20개. 통과로 계산하지 않았다.
- Java 컴파일 및 `bootJar` 성공, `git diff --check` 통과.
- 변경 범위: 백엔드 Java 58개 파일, 테스트 Java 45개 파일, 의존성 확인 도구 1개, 이 보고서. Android/iOS/프론트/배포·인프라 파일 변경 없음.
- 기존 HTTP/STOMP mapping annotation 변경 없음. 요청·응답 DTO 필드를 제거·이름 변경하지 않고 검증 상한을 추가했다.
- 결과: `build/admin-release-verification/reports/tests/test/index.html`, 개별 결과는 `build/admin-release-verification/test-results/test/TEST-*.xml`.

실행은 로컬 JDK 17, 기존 Gradle wrapper, `tools/isolated-admin-tests.init.gradle`로 외부 application 설정을 제외한 더미/H2 환경이다. 인증·방 권한·차단·회전 경합·이메일 재사용·광고/추천 동시성·입력 길이·민감 로그·CORS/헤더를 회귀 검증한다.

```powershell
.\gradlew.bat --no-daemon -I tools/isolated-admin-tests.init.gradle -PadminReleaseVerification test bootJar
```

- 관리자·운영 지표의 익명 401/일반 사용자 403, 허용되지 않은 CORS 출처 거절, HTTPS 보안 헤더를 실제 SecurityConfig로 검증했다.
- 반복 실행에서 즉시 전달을 기대한 채팅·알림 테스트의 timestamp 정밀도/예약시각 경계 실패가 드러나 테스트 데이터와 시계 정밀도를 명시했다. 실제 운영 전달 코드나 assertion은 완화하지 않았다.
- 검증용 JAR은 외부 설정을 제외한 빌드 결과다. 운영용 패키징·설정 확인 및 배포 검증을 대체하지 않는다.

제한: 실제 Redis Lua 실행/클러스터, MySQL 고유 격리 수준 동시성, 실결제 전체 JWS, 배포 이미지, 모바일 실기기 재연결 및 신구 서버 혼합 전환은 확인하지 않았다. Redis 원자성 테스트는 모의 저장소 경계이고 H2는 MySQL 동작의 완전한 대체가 아니다.
