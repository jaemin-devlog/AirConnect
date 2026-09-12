# 이강현 이력서 초안

## Introduction

결제/광고 보상처럼 돈과 신뢰가 걸린 기능은 중복 처리와 상태 전이를 설계 단계에서 먼저 막는 백엔드 개발자입니다. AirConnect를 App Store와 Play Store에 출시·운영하며 IAP, AdMob SSV, Redis 매칭 큐, 신고/제재, 관리자 운영 지표를 직접 설계하고 구현했습니다.

외부 API와 AI 응답도 “정상 응답을 믿는 코드”가 아니라 검증 가능한 파이프라인으로 다룹니다. MoneyWay에서는 OpenAI 응답이 DB에 없는 장소를 만들거나 예산을 초과하는 문제를 후보 제한과 JSON/슬롯/예산 검증으로 통제했고, mock 기반 30개 변형 테스트에서 DB 외 장소·슬롯 누락·예산 초과를 0건으로 만들었습니다.

## Link

- GitHub: https://github.com/kanghyun-e

## Personal Information

- 이름: 이강현 / Lee Kanghyun
- 직무: Backend Engineer
- 연락처: 010-9130-6469
- 학력: 한서대학교 항공소프트웨어공학과, 2021.03 ~ 2027.02 졸업예정, GPA 3.81 / 4.5
- 주요 기술: Java, Spring Boot, Spring Data JPA, QueryDSL, MyBatis, MySQL 8, Redis 7, WebSocket(STOMP), Docker, Docker Compose, Nginx, GitHub Actions, Apple/Google IAP, AdMob SSV, OpenAI API, TourAPI

## Project

### [AirConnect] 대학생 소셜 매칭·티켓 경제 운영 백엔드 개발

- 기간: 2025.11 ~ 현재
- 역할: 백엔드 리드
- 서비스: 대학생 1:1/그룹 매칭, 실시간 채팅, 티켓 결제·광고 보상, 신고/제재와 관리자 운영 기능을 포함한 iOS/Android 앱 백엔드
- 기술: Java, Spring Boot, Spring Data JPA, QueryDSL, MySQL 8, Redis 7, WebSocket(STOMP), Docker Compose, Nginx, GitHub Actions, Apple/Google IAP, AdMob SSV
- 운영 단계: 정식 홍보 전 기능 검증 단계, 누적 가입자 18명 / 온보딩 완료 11명

주요 구현

- 클라이언트 영수증만으로 티켓을 지급하면 위조·재전송에 취약한 문제를 App Store/Google Play 서버 검증, transactionId/purchaseToken 기준 중복 주문 조회, IapOrder 비관적 락으로 처리했습니다. 같은 appAccountToken/transactionId를 동시에 2번 검증하는 테스트에서 1건은 GRANTED, 1건은 ALREADY_GRANTED로 수렴하도록 구현했습니다.
- Apple 결제의 appAccountToken, transactionId, productId, environment, originalTransactionId 일관성 검증을 추가해 다른 사용자·다른 환경·환불/취소 거래가 티켓 지급으로 이어지지 않도록 거절 경로를 분리했습니다.
- AdMob SSV 콜백은 signature/key_id, custom_data(sessionKey), transaction_id를 검증한 뒤 세션을 PESSIMISTIC_WRITE로 잠그고 티켓 원장 refType/refId 중복 확인을 거쳐 반복 콜백에도 보상이 1회만 반영되도록 했습니다.
- Redis 기반 그룹매칭 대기열은 재시작·TTL 만료 시 DB 상태와 큐가 어긋날 수 있어 MySQL의 QUEUE_WAITING 방 목록을 기준으로 Redis 리스트와 queueToken 매핑을 재구성하는 reconcile 로직을 만들었습니다.
- 신고/차단/제재 도메인은 중복 신고 윈도우와 동시성 테스트를 추가해 같은 신고 2건 동시 유입 시 1건 저장, 1건 중복 거절로 처리했고, 운영자가 신고→검토→제재→매칭 제외 흐름을 추적하도록 구성했습니다.
- 관리자 콘솔에 API 사용량, 1:1/그룹 매칭 퍼널, 알림 Outbox 상태, 7개 데이터 정합성 점검, 감사 로그를 제공해 운영 이슈를 기능 단위로 확인할 수 있게 했습니다.

### [MoneyWay] 제주 AI 여행 일정 생성 서비스 백엔드 개발

- 기간: 2025.05 ~ 2025.11
- 역할: 백엔드 개발
- 서비스: 제주 관광지 데이터와 AI를 활용한 여행 일정 생성 서비스
- 기술: Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL 8, Docker, OpenAI API, TourAPI
- 성과: 한국관광공사 2025 관광데이터 활용 공모전 우수상

주요 구현

- TourAPI 기반 제주 관광지 수집과 엑셀 업로드 기반 식당/카페 등록 흐름을 구성하고, 카테고리·검색·페이징을 포함한 REST API 48개를 구현했습니다.
- OpenAI 응답이 DB에 없는 장소를 생성하거나 슬롯을 누락하고 예산을 초과할 수 있는 문제를 입력 후보 DB 제한, JSON 형식 검증, 슬롯/예산 검증 파이프라인으로 제어했습니다.
- 후보 장소가 부족해 일정 생성이 실패하는 지역을 위해 탐색 반경을 10km에서 시작해 후보 부족 시 1.5배씩 최대 50km까지 확장하도록 구현했습니다.
- mock 기반 30개 변형 테스트에서 DB 외 장소 생성, 슬롯 누락, 예산 초과를 모두 0건으로 검증했습니다.

## Education & Activities

- 2026.03 ~ 현재: 멋쟁이사자처럼 한서대학교 14기 부대표
- 2025.09 ~ 2025.11: 한국관광공사 2025 관광데이터 활용 공모전 우수상
- 2025.07 ~ 2025.08: 멋쟁이사자처럼 전국 연합 해커톤 2차 진출
- 2025.03 ~ 2025.12: 멋쟁이사자처럼 한서대학교 13기 일반 부원
- 2022.04.22: 한국사능력검정시험, 58-107657
