# Git 변경사항 비교 정리

기준: 현재 워킹트리의 `git diff` 및 신규 추가 파일 기준  
작성일: 2026-04-25  
요약 규모: `28개 수정 파일 + 2개 신규 파일`, `732 insertions / 94 deletions`

## 1. 이번 변경의 목적

이번 변경은 Android 기기에서 알림 수신 순간 또는 채팅/팀룸 이벤트 수신 순간에 렉, 버벅임, 과도한 알림 burst가 발생할 수 있는 서버 경로를 완화하는 데 초점이 있다.

핵심 방향은 다음과 같다.

- iOS 전용 발송 포맷은 유지
- Android 쪽 push 강도와 빈도를 완화
- DB 트랜잭션 안에서 하던 외부 전송을 commit 이후로 이동
- foreground/viewing 상태를 더 정확하고 저렴하게 판단
- team-room, admin 공지, outbox worker의 burst 가능성을 줄임

## 2. 바뀌지 않은 것

이번 변경에서 직접 바뀌지 않은 항목은 아래와 같다.

- HTTP API endpoint, request/response DTO 계약
- DB migration
- iOS 전용 FCM/APNs message builder 로직
- notification 도메인 모델 자체의 외부 계약

즉, 외부 API 계약 변경이 아니라 서버 내부 발송 정책과 이벤트 처리 순서를 조정한 변경이다.

## 3. 변경 파일 요약

### 신규 파일

- `src/main/java/univ/airconnect/global/tx/AfterCommitExecutor.java`
- `src/main/java/univ/airconnect/notification/service/AndroidPushSendGapService.java`

### 주요 수정 파일

- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidPushPolicy.java`
- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidPushPolicyResolver.java`
- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidFcmMessageBuilder.java`
- `src/main/java/univ/airconnect/notification/service/AndroidChatPushCoalescingService.java`
- `src/main/java/univ/airconnect/notification/service/NotificationService.java`
- `src/main/java/univ/airconnect/notification/service/NotificationDeliveryGuard.java`
- `src/main/java/univ/airconnect/notification/service/NotificationOutboxWorker.java`
- `src/main/java/univ/airconnect/chat/service/ChatService.java`
- `src/main/java/univ/airconnect/global/security/stomp/StompHandler.java`
- `src/main/java/univ/airconnect/groupmatching/service/GMatchingEventPublisher.java`
- `src/main/java/univ/airconnect/groupmatching/service/GMatchingService.java`
- `src/main/java/univ/airconnect/groupmatching/service/GRedisMatchingPushService.java`
- `src/main/java/univ/airconnect/admin/AdminService.java`
- `src/main/java/univ/airconnect/user/repository/UserRepository.java`

### 테스트 수정 파일

- `src/test/java/univ/airconnect/notification/service/*`
- `src/test/java/univ/airconnect/chat/service/*`
- `src/test/java/univ/airconnect/groupmatching/service/*`
- `src/test/java/univ/airconnect/global/security/stomp/StompHandlerTest.java`
- `src/test/java/univ/airconnect/admin/AdminServiceTest.java`
- `src/test/java/univ/airconnect/matching/service/MatchingServiceRaceTest.java`

## 4. 영역별 상세 비교

## 4-1. Android push 정책 완화

### 기존

- `CHAT_MESSAGE_RECEIVED` 가 Android에서 상대적으로 강한 우선순위로 발송되었다.
- `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_LEFT`, `TEAM_MEMBER_READY_CHANGED` 도 일반 Android notification payload로 발송되었다.
- Android notification sound 설정이 사실상 항상 붙는 구조였다.

### 변경 후

- `AndroidPushPolicy` 에 `soundEnabled` 필드가 추가되었다.
- `CHAT_MESSAGE_RECEIVED` 는 `high` 계열이 아니라 `normal` 계열 정책으로 낮아졌다.
- `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_LEFT`, `TEAM_MEMBER_READY_CHANGED` 는 `quietNormal(...)` 정책으로 내려갔다.
- `AndroidFcmMessageBuilder` 는 정책상 `soundEnabled == true` 인 경우에만 sound를 붙인다.
- `NotificationService` 는 Android의 `TEAM_MEMBER_READY_CHANGED` push outbox 생성 자체를 건너뛴다.

### 개선점

- Android에서 chat/team activity 알림이 OS 레벨에서 과하게 튀는 가능성을 줄인다.
- 저가치 이벤트는 진동/사운드/heads-up 가능성을 낮추고, 중요한 이벤트와 분리된다.
- iOS 정책을 건드리지 않고 Android만 별도 완화한다.

### 관련 파일

- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidPushPolicy.java`
- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidPushPolicyResolver.java`
- `src/main/java/univ/airconnect/notification/service/fcm/android/AndroidFcmMessageBuilder.java`
- `src/main/java/univ/airconnect/notification/service/NotificationService.java`

## 4-2. Android chat burst 완화

### 기존

- Android chat coalescing window 가 `1초`였다.
- 짧은 burst 상황에서 outbox가 비교적 자주 생길 수 있었다.

### 변경 후

- `AndroidChatPushCoalescingService.COALSCING_WINDOW` 가 `1초 -> 3초`로 증가했다.

### 개선점

- 같은 채팅방에서 짧은 시간에 들어오는 연속 메시지를 더 넓게 묶을 수 있다.
- Android 단말이 burst 알림을 연속 수신하는 빈도를 줄인다.

### 관련 파일

- `src/main/java/univ/airconnect/notification/service/AndroidChatPushCoalescingService.java`

## 4-3. Android 단말별 send gap 제한 추가

### 기존

- outbox worker 는 batch 단위로 처리했지만, 같은 Android 기기에 대해 직전에 보낸 동일 성격의 push 간격을 별도로 제어하지 않았다.
- FCM collapseKey/coalescing 외에 실제 단말 기준 send gap 제어가 없었다.

### 변경 후

- `AndroidPushSendGapService` 가 신규 추가되었다.
- Redis 키 `push:android:gap:{pushDeviceId}:{notificationType}:{scope}` 를 사용해 최근 발송 시각을 기록한다.
- 적용 대상은 현재 다음과 같다.
  - `CHAT_MESSAGE_RECEIVED`: 같은 chat room 기준 10초
  - `TEAM_MEMBER_JOINED`, `TEAM_MEMBER_LEFT`: 같은 team room 기준 20초
  - `SYSTEM_ANNOUNCEMENT`: 글로벌 기준 3초
- `NotificationDeliveryGuard` 는 gap이 활성화돼 있으면 즉시 발송하지 않고 `DEFER(ANDROID_DEVICE_SEND_GAP)` 처리한다.
- `NotificationOutboxWorker` 는 실제 발송 성공 시 send gap 기준 시각을 기록한다.

### 개선점

- 같은 단말에 back-to-back push가 몰리는 상황을 줄인다.
- drop이 아니라 defer 방식이라 중요한 사용자 경험을 일괄적으로 깨지 않는다.
- Android 전용 정책이라 iOS에는 영향이 없다.

### 관련 파일

- `src/main/java/univ/airconnect/notification/service/AndroidPushSendGapService.java`
- `src/main/java/univ/airconnect/notification/service/NotificationDeliveryGuard.java`
- `src/main/java/univ/airconnect/notification/service/NotificationOutboxWorker.java`

## 4-4. 외부 전송을 after-commit 으로 이동

### 기존

- 채팅 저장, 팀룸 상태 변경, Redis publish, STOMP push, notification enqueue 일부가 같은 트랜잭션 흐름 안에서 처리됐다.
- commit 이전에 외부 전송이 먼저 나갈 수 있는 구조였다.

### 변경 후

- `AfterCommitExecutor` 가 신규 추가되었다.
- 트랜잭션이 활성 상태이면 `afterCommit` 에서 작업을 실행하고, 아니면 즉시 실행한다.
- 아래 경로들이 after-commit 으로 이동했다.
  - chat Redis publish
  - chat room list STOMP update
  - chat message notification enqueue
  - team-room STOMP realtime publish
  - group-matched Redis dispatch
  - groupmatching notification create/createAndEnqueue

### 개선점

- 롤백될 트랜잭션에서 외부 이벤트가 먼저 나가는 위험이 줄었다.
- 트랜잭션 내부 fan-out 을 줄여서 저장 경로를 덜 무겁게 만든다.
- 알림과 STOMP 이벤트가 실제 커밋된 상태 기준으로 동작한다.

### 관련 파일

- `src/main/java/univ/airconnect/global/tx/AfterCommitExecutor.java`
- `src/main/java/univ/airconnect/chat/service/ChatService.java`
- `src/main/java/univ/airconnect/groupmatching/service/GMatchingEventPublisher.java`
- `src/main/java/univ/airconnect/groupmatching/service/GMatchingService.java`
- `src/main/java/univ/airconnect/groupmatching/service/GRedisMatchingPushService.java`

## 4-5. 채팅 foreground/viewing 판정 개선

### 기존

- `isUserViewingRoom` 이 `roomSessionSet` 기반으로 session 전체를 보고, 다시 session -> user 매핑을 확인하는 식의 비용이 더 큰 방식이었다.
- stale session 정리와 viewer 판정이 같은 경로에 섞여 있었다.

### 변경 후

- Redis set 키 `chat:view:user-room:{userId}:{roomId}` 를 새로 사용한다.
- subscribe, session-room mapping, unsubscribe, disconnect 시 이 키를 함께 갱신한다.
- `isUserViewingRoom` 은 이제 해당 키의 set size만 보고 판단한다.

### 개선점

- viewer 판단 비용이 줄었다.
- foreground 사용자를 더 정확히 판단해서 불필요한 chat push를 줄일 수 있다.
- 채팅방을 실제 보고 있는 상대에게 OS push가 나가는 false positive를 줄인다.

### 관련 파일

- `src/main/java/univ/airconnect/chat/service/ChatService.java`

## 4-6. team-room viewer suppression 추가

### 기존

- team-room STOMP 구독과 push 발송이 별개로 움직였고, 같은 팀룸을 보고 있는 사용자에게도 저가치 이벤트 push가 그대로 나갈 수 있었다.
- team-room viewer 상태를 별도 Redis key로 추적하지 않았다.

### 변경 후

- Redis 키 `matching:session-subscriptions:{sessionId}` 와 `matching:view:user-room:{userId}:{teamRoomId}` 를 추가로 사용한다.
- `StompHandler` 가 matching team-room subscribe/unsubscribe/disconnect 에서 해당 presence를 갱신한다.
- `GMatchingService.sendGroupNotification(...)` 에서 다음 타입은 viewer이면 OS push enqueue를 하지 않고 `notificationService.create(...)` 만 수행한다.
  - `TEAM_MEMBER_JOINED`
  - `TEAM_MEMBER_LEFT`
  - `TEAM_MEMBER_READY_CHANGED`

### 개선점

- 팀룸을 이미 보고 있는 사용자에게 STOMP와 OS push가 동시에 중복되는 문제를 완화한다.
- 저가치 team activity는 in-app event 중심, 중요한 팀 이벤트는 push 유지라는 정책 분리가 생겼다.

### 관련 파일

- `src/main/java/univ/airconnect/global/security/stomp/StompHandler.java`
- `src/main/java/univ/airconnect/groupmatching/service/GMatchingService.java`

## 4-7. 관리자 공지 fan-out 방식 개선

### 기존

- 공지 발송 시 전체 사용자 ID 리스트를 한 번에 조회하고 순회하는 구조였다.
- 큰 사용자 수에서 메모리와 트랜잭션 길이가 커질 수 있었다.

### 변경 후

- `AdminService.broadcastNotice(...)` 는 `@Transactional(propagation = NOT_SUPPORTED)` 로 변경되었다.
- `UserRepository` 에 pageable 기반 ID 조회 메서드가 추가되었다.
- 공지 발송은 `500명 단위 페이지`로 잘라서 순회한다.

### 개선점

- 전체 공지 발송 시 한 번에 모든 수신자를 메모리에 올리지 않는다.
- 대량 공지 시 fan-out burst와 장트랜잭션 리스크를 줄인다.

### 관련 파일

- `src/main/java/univ/airconnect/admin/AdminService.java`
- `src/main/java/univ/airconnect/user/repository/UserRepository.java`

## 5. 테스트 변경 요약

이번 변경은 메인 코드만 바뀐 것이 아니라, 변경된 정책과 실행 순서에 맞춰 테스트도 같이 보강되었다.

### notification 계열

- Android policy 변경 검증
- `soundEnabled` 조건부 sound 설정 검증
- Android ready-changed push skip 검증
- device send gap defer 검증
- worker 성공 시 send gap 기록 경로 주입 보강
- 기존 chat coalescing 테스트의 시간 의존성 제거

### chat / stomp 계열

- chat viewer presence key 추적 검증
- matching subscribe/unsubscribe/disconnect 시 team-room presence 갱신 검증

### groupmatching 계열

- team-room viewer suppression 시 `createAndEnqueue` 대신 `create` 가 호출되는지 검증
- team-room session subscription 추적 검증
- after-commit 연동에 맞춰 publisher/service 테스트 수정

### 기타

- `MatchingServiceRaceTest` 에는 전체 테스트 수행 시 필요한 mock 보강이 추가되었다.

## 6. 기존 대비 개선 포인트 정리

한 줄로 요약하면 아래와 같다.

- 기존: Android 채팅/팀 이벤트가 상대적으로 강하고 자주 나갈 수 있었고, 일부 외부 전송이 트랜잭션 안에서 함께 실행됐다.
- 변경 후: Android만 조절해서 더 약하고 덜 자주 보내며, commit 이후 기준으로 STOMP/Redis/push를 정리하고, viewer면 저가치 push를 줄이는 구조가 되었다.

구체적인 개선 포인트는 다음과 같다.

- Android chat push 강도 완화
- team activity push 조용한 정책 적용
- `TEAM_MEMBER_READY_CHANGED` Android push 제거
- chat coalescing 확대
- device별 send gap 제어 추가
- chat/team-room viewer 판단 정확도 상승
- STOMP/Redis/push after-commit 실행
- admin notice fan-out paging

## 7. 영향 범위 정리

### 직접 영향이 있는 범위

- Android 알림 강도
- Android burst 억제
- 채팅/팀룸의 viewer 기반 push 억제
- outbox 처리 타이밍
- 관리자 공지 대량 발송 방식

### 직접 영향이 없는 범위

- iOS 전용 builder 정책
- 외부 API 계약
- DB schema

## 8. 최종 메모

이번 변경은 “모든 알림을 늦춘다”거나 “서버 구조를 전부 뜯어고친다”는 성격이 아니다.  
실제로 문제가 될 가능성이 높았던 Android 특화 경로, 트랜잭션 내부 fan-out, viewer 중복 수신, 대량 공지 burst를 중심으로 좁게 보정한 패치다.

검증 결과는 다음과 같다.

- `./gradlew.bat compileJava --no-daemon` 통과
- `./gradlew.bat test --no-daemon` 통과
