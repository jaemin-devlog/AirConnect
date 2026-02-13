# AirConnect (에어커넥트) - 인연의 활주로 ✈️

<p align="center">
  <img src="Logo.png" width="600" alt="AirConnect Logo" />
</p>

**AirConnect**는 *항공 컨셉으로 1:1 소개팅부터 다대다 과팅까지 한눈에 살펴보고 소통하는 공간*을 제공함으로써  
사용자에게 **설렘과 연결 경험**을 주는 것을 목표로 하는 매칭 서비스입니다.

> 핵심 키워드: **항공 컨셉 / 선 탐색 후 가입 / 1:1 소개팅 / 다대다 과팅 / 마일리지 / 실시간 채팅**

<br>

# 프로젝트 아키텍처

> (여기에 아키텍처 다이어그램 이미지를 넣어주세요)


- **API**: REST + WebSocket(STOMP)
- **인증**: OAuth2(카카오/애플) + JWT + Spring Security
- **데이터**: MySQL + JPA/QueryDSL + Flyway
- **캐시/세션**: Redis
- **파일 업로드**: S3 + CloudFront
- **배포**: Docker Compose + Nginx + GitHub Actions

<br>

# 프로젝트 목표

**1) “선 탐색, 후 가입”으로 진입 장벽 최소화**
- 소셜 로그인만으로 먼저 둘러보고, 실제 액션 시점에만 필수 프로필을 수집합니다.

**2) 신뢰 가능한 매칭을 위한 단계적 정보 수집**
- 기본 정보 → 필수 매칭 정보 → 추가 매력 정보로 단계별 입력 구조를 설계합니다.

**3) 실시간 커뮤니케이션 경험 강화**
- 1:1 채팅 및 그룹 채팅(단톡) + 약속 시간 설정 & 리마인드 알림까지 제공합니다.

**4) 운영 가능한 서비스 지향**
- 마일리지 경제(지급/소모/환급), 알림/공지(관제탑), 로그/모니터링까지 포함해 운영 관점까지 고려합니다.

<br>

# 사용 기술

| 구분 | 기술 스택 |
| --- | --- |
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.3.x |
| 빌드 도구 | Gradle |
| DB | MySQL 8.x |
| ORM | Spring Data JPA + QueryDSL |
| 마이그레이션 | Flyway |
| 캐시/세션 | Redis 7.x |
| 보안/인증 | Spring Security 6.x + JWT + OAuth2 |
| API | REST + WebSocket(STOMP) |
| 문서화 | Swagger (Springdoc) |
| 파일 업로드 | AWS S3 + CloudFront |
| 테스트 | JUnit5 + Mockito |
| 로그/모니터링 | Logback (+ Discord Webhook) |
| 배포/인프라 | Docker Compose + GitHub Actions + Nginx |
| 인프라 환경 | 가비아 (Ubuntu 22.04 LTS) |
| 코드 관리 | GitHub / Git Flow |

<br>
