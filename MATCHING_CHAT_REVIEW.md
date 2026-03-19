# Matching / Chat 코드 리뷰

- 리뷰 일시: 2026-03-19
- 리뷰 기준: 현재 워킹트리 기준 정적 리뷰 (`matching`, `chat`, WebSocket/Security/Redis 연동 코드)
- 런타임 검증: 제한적
  - `.\gradlew test` 실행을 시도했지만 현재 환경에서는 Gradle wrapper 배포본 확보 문제로 정상 완료하지 못함
  - 현재 테스트 코드는 `src/test/java/univ/airconnect/AirConnectApplicationTests.java`의 `contextLoads()` 1건만 존재함

## 한줄 결론

현재 구현은 **임시 팀방 생성 → 전원 준비 → Redis 큐 등록 → 최종 그룹 채팅방 자동 생성**까지의 서버 상태 전이는 대체로 맞게 잡혀 있다.  
다만 **Step 4의 “앱이 켜져 있으면 즉시 이동” / “앱이 꺼져 있으면 푸시 알림”**은 아직 완성되지 않았고, **네이티브 앱 연동은 가능하지만 STOMP 기반으로 설계되어 있어 모바일 클라이언트 요구사항이 분명하다.**

## UX 기준 충족 여부

### Step 1. 팀 빌딩 대기방

- `2인/3인 팀` 지원: `TeamSize`로 구현됨
- `팀원 초대/입장` 지원: 공개방 입장, 비공개 초대코드 입장 모두 구현됨
- `팀 전용 임시 채팅방` 지원: 팀방 생성 시 그룹 채팅방을 같이 생성함
- `공개/비공개` 지원: `TeamVisibility`로 구현됨
- `매칭 큐 미진입 상태` 유지: 생성 직후 `OPEN`, 정원 충족 시 `READY_CHECK`
- 주의: `성별 필터링`은 **상대 팀 매칭 필터는 있음**. 하지만 **팀원 입장 시 사용자 성별 검증은 서버에 없음**

### Step 2. 준비 완료

- 정원 충족 시 자동으로 `READY_CHECK` 단계로 전환됨
- 각 팀원 `ready` 상태 저장 로직 존재
- 전원 준비 완료 후 방장만 `startMatching()` 가능하도록 서비스 레벨에서 막고 있음

### Step 3. 실시간 매칭

- 큐 원천은 Redis 리스트(`matching:queue:{teamSize}`)임
- 앱은 `/queue` API로 `position`, `aheadCount`, `totalWaitingTeams` 조회 가능
- 대기 중 임시 채팅방 유지됨
- 앱이 꺼져도 서버 측 큐는 유지됨
- 주의: 큐는 Redis가 원천이 맞지만, **큐 처리 트리거/배수 처리 방식에는 개선 포인트가 있음**

### Step 4. 매칭 성공

- 서버가 상대 팀을 찾으면 최종 그룹 채팅방을 즉시 생성함
- 기존 임시방은 `MATCHED -> CLOSED` 처리됨
- 수락/거절 단계 없이 바로 최종 그룹 채팅방으로 들어가는 정책과 일치함
- 미충족:
  - **앱 ON 상태 즉시 이동용 matching 전용 실시간 이벤트가 없음**
  - **앱 OFF 상태 복귀 유도용 푸시 알림 구현이 없음**

## 잘 구현된 점

1. **상태 모델은 비교적 명확함**
   - `OPEN -> READY_CHECK -> QUEUE_WAITING -> MATCHED -> CLOSED/CANCELLED` 흐름이 코드에 드러남
   - 관련 코드: `src/main/java/univ/airconnect/matching/domain/TemporaryTeamRoomStatus.java`

2. **임시방/최종방 분리가 잘 되어 있음**
   - 임시 팀방은 별도 엔티티, 최종 그룹 채팅방은 `FinalGroupChatRoom`으로 분리됨
   - 관련 코드:
     - `src/main/java/univ/airconnect/matching/domain/entity/TemporaryTeamRoom.java`
     - `src/main/java/univ/airconnect/matching/domain/entity/FinalGroupChatRoom.java`

3. **매칭 큐의 진실 원천을 Redis로 둔 방향은 맞음**
   - 큐 등록/삭제/순번 조회가 Redis 기반으로 구현됨
   - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:211`, `src/main/java/univ/airconnect/matching/service/MatchingService.java:260`, `src/main/java/univ/airconnect/matching/service/MatchingService.java:700`

4. **채팅은 REST + STOMP/WebSocket 조합으로 네이티브 앱에서도 사용 가능함**
   - REST: 방 목록/메시지 히스토리/읽음 처리
   - STOMP: 실시간 메시지 수신
   - 관련 코드:
     - `src/main/java/univ/airconnect/chat/controller/ChatRoomController.java`
     - `src/main/java/univ/airconnect/chat/controller/ChatController.java:27`
     - `src/main/java/univ/airconnect/global/config/WebSocketConfig.java:22`

## 핵심 이슈

### 1. [높음] 매칭 성공 즉시 이동용 “matching 전용 실시간 이벤트”가 없음

- 현재 실시간 브로드캐스트는 채팅 이벤트만 전송함
  - `RedisSubscriber`는 `/sub/chat/room/{roomId}` 로만 전송
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/RedisSubscriber.java:40`
- 반면 매칭 결과는 REST polling 기반 조회만 있음
  - `/queue`, `/final-room`
  - 관련 코드:
    - `src/main/java/univ/airconnect/matching/controller/MatchingController.java:160`
    - `src/main/java/univ/airconnect/matching/controller/MatchingController.java:212`
- 영향:
  - 앱이 켜져 있어도 서버가 “최종 방 ID”를 authoritative 하게 밀어주지 못함
  - 현재 구조에서는 클라이언트가 polling 하거나, 임시방 시스템 메시지를 해석해서 추가 조회해야 함

### 2. [높음] 푸시 알림 인프라가 없어 Step 4의 앱 종료 복귀 UX를 충족하지 못함

- 코드베이스 검색 기준으로 `deviceToken`, `FCM`, `Firebase`, `APNS`, `notification` 관련 서버 코드가 없음
- 영향:
  - 앱이 종료/백그라운드 상태일 때 매칭 성공을 사용자에게 보장성 있게 전달할 수 없음

### 3. [높음] 팀원 입장 시 성별 검증이 서버에 없음

- 팀방은 `teamGender`와 `opponentGenderFilter`를 가짐
  - 관련 코드: `src/main/java/univ/airconnect/matching/domain/entity/TemporaryTeamRoom.java:58`, `src/main/java/univ/airconnect/matching/domain/entity/TemporaryTeamRoom.java:73`
- 매칭 시에도 필터는 “상대 팀 성별” 기준으로만 동작함
  - 관련 코드: `src/main/java/univ/airconnect/matching/domain/entity/TemporaryTeamRoom.java:315`
- 실제 팀원 입장 로직에는 사용자 성별 검증이 없음
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:432`
- `User` / `UserProfile` 쪽에도 성별 필드가 없음
  - 관련 코드:
    - `src/main/java/univ/airconnect/user/domain/entity/User.java:23`
    - `src/main/java/univ/airconnect/user/domain/entity/UserProfile.java:16`
- 영향:
  - 예를 들어 남자 팀방에 여자 사용자가 들어가도 서버는 막지 못함
  - “성별 필터링 지원”을 완전하게 만족한다고 보기 어려움

### 4. [중간] 큐 처리가 `startMatching()` 호출 시 1회만 돌고, 한 번에 1쌍만 매칭함

- 큐 처리는 `startMatching()` 내부에서만 직접 호출됨
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:214`
- `processQueue()`는 첫 번째 매칭 가능한 팀 쌍을 찾으면 바로 `return completeMatch(...)`로 종료함
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:329`
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:336`
- 영향:
  - 대기열에 이미 여러 팀이 있어도 한 번에 1쌍만 처리됨
  - 뒤쪽에 있는 다른 호환 팀들은 **다음 `startMatching()` 트리거가 없으면 계속 대기**할 수 있음

### 5. [중간] 큐 순번/앞 팀 수가 “실제 매칭 가능성”과 다를 수 있음

- 앱에 내려주는 값은 Redis 리스트 내 단순 위치값임
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:260`
  - 관련 코드: `src/main/java/univ/airconnect/matching/service/MatchingService.java:268`
- 실제 매칭 로직은 단순 FIFO 하나가 아니라 `canMatchWith()`로 호환 팀을 탐색함
  - 관련 코드:
    - `src/main/java/univ/airconnect/matching/service/MatchingService.java:329`
    - `src/main/java/univ/airconnect/matching/domain/entity/TemporaryTeamRoom.java:315`
- 영향:
  - UI에 보여주는 `앞에 남은 팀 수`가 사용자가 체감하는 실제 대기순서와 어긋날 수 있음

### 6. [중간] `matching`과 `chat`의 성공 응답 포맷이 다름

- `chat`은 `ApiResponse<T>` 래핑 사용
  - 관련 코드: `src/main/java/univ/airconnect/chat/controller/ChatRoomController.java:39`
- `matching`은 DTO를 바로 반환
  - 관련 코드: `src/main/java/univ/airconnect/matching/controller/MatchingController.java:47`
- 영향:
  - iOS/Android 클라이언트에서 공통 네트워크 레이어를 짤 때 분기 비용이 늘어남
  - traceId 처리도 일관되지 않음

### 7. [중간] 채팅 실시간 구독 토픽이 누적되고 해제되지 않음

- 방 구독 시 `topics.computeIfAbsent(...)`로 Redis listener를 등록함
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/ChatService.java:199`
- 연결 종료 시에는 세션 키만 지우고 listener 제거는 하지 않음
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/ChatService.java:236`
- 영향:
  - 장기 운영 시 방 개수가 많아질수록 메모리/리스너 관리 비용이 누적될 수 있음

### 8. [중간] 일반 채팅 메시지 전송은 Redis publish 성공에 강하게 결합되어 있음

- 메시지는 DB 저장 후 Redis publish를 바로 수행함
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/ChatService.java:264`
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/ChatService.java:280`
- `publishToRedis()`는 직렬화 실패 시 런타임 예외를 던짐
  - 관련 코드: `src/main/java/univ/airconnect/chat/service/ChatService.java:449`
- 영향:
  - Redis publish 실패/직렬화 실패가 곧 메시지 전송 실패로 이어질 수 있음
  - “메시지는 저장됐는데 실시간만 실패” 같은 graceful degradation이 현재 일반 메시지 전송에는 없음

### 9. [낮음] `matching`만 사용자 식별 방식이 별도로 분리되어 있음

- `chat`은 `@CurrentUserId`를 사용
- `matching`은 reflection + `authentication.getName()` fallback으로 userId를 추출함
  - 관련 코드: `src/main/java/univ/airconnect/matching/controller/MatchingController.java:286`
- 영향:
  - 현재 principal 구현에선 동작 가능하지만, 인증 principal 변경 시 `matching`만 따로 깨질 가능성이 있음

### 10. [낮음] 자동화된 테스트가 사실상 없음

- 현재 테스트는 `contextLoads()` 한 건뿐임
  - 관련 코드: `src/test/java/univ/airconnect/AirConnectApplicationTests.java:6`
- 영향:
  - `matching`의 상태 전이/Redis 큐/`chat`의 REST+STOMP 연동 회귀를 자동으로 잡지 못함

## 네이티브 앱(iOS/Android) 연동 판단

### 가능한 부분

- 브라우저 전용 구조는 아님
- HTTP API + JWT 인증 + STOMP over WebSocket 조합이라서 네이티브 앱에서도 연동 가능함
- 실시간 채팅 연결 정보는 아래와 같음
  - WebSocket endpoint: `/ws-stomp`
  - Publish prefix: `/pub`
  - Subscribe prefix: `/sub`
  - 메시지 송신: `/pub/chat/message`
  - 방 구독: `/sub/chat/room/{roomId}`
  - 관련 코드:
    - `src/main/java/univ/airconnect/global/config/WebSocketConfig.java:22`
    - `src/main/java/univ/airconnect/global/config/WebSocketConfig.java:24`
    - `src/main/java/univ/airconnect/global/config/WebSocketConfig.java:30`
    - `src/main/java/univ/airconnect/chat/controller/ChatController.java:27`

### 전제 조건

- 앱은 **plain WebSocket이 아니라 STOMP client**를 써야 함
- STOMP `CONNECT` 시 `Authorization: Bearer {token}` native header를 넣어야 함
  - 관련 코드: `src/main/java/univ/airconnect/global/security/stomp/StompHandler.java:57`
  - 관련 코드: `src/main/java/univ/airconnect/global/security/stomp/StompHandler.java:110`
- 채팅방 구독은 서버에서 멤버십 검증을 함
  - 관련 코드: `src/main/java/univ/airconnect/global/security/stomp/StompHandler.java:89`

### 아직 부족한 부분

- 매칭 성공 이벤트가 채팅 이벤트에 섞여 있어 네이티브 앱 화면 전환 트리거가 약함
- 앱 종료 상태 복귀용 푸시가 없음
- 응답 포맷이 패키지별로 달라 모바일 공통 네트워크 계층이 불편함

## 우선 수정 추천

1. **matching 전용 실시간 이벤트 추가**
   - 예: `/sub/matching/team-room/{teamRoomId}` 또는 `/sub/matching/user/{userId}`
   - payload에 `teamRoomId`, `status`, `finalGroupRoomId`, `finalChatRoomId` 포함

2. **모바일 푸시 인프라 추가**
   - 사용자별 device token 저장
   - 매칭 성공 시 FCM/APNs 발송

3. **사용자 성별을 서버 원천 데이터로 추가하고 팀원 입장 시 검증**
   - `User` 또는 `UserProfile`에 명시적 성별 필드 추가
   - `joinPublicRoom` / `joinPrivateRoomByInviteCode` 경로에서 팀 성별과 대조

4. **큐 처리 워커를 루프/스케줄러 기반으로 분리**
   - `startMatching()` 1회성 호출에만 의존하지 않도록 수정
   - 한 번 실행 시 가능한 pair를 모두 drain 하도록 개선

5. **matching/chat API 응답 포맷 통일**
   - 둘 다 `ApiResponse<T>`로 맞추는 것을 권장

6. **자동화 테스트 추가**
   - `matching`: 생성/입장/ready/start/queue/match/final-room
   - `chat`: room list/history/read/send/WebSocket subscribe authorization

## 최종 판단

- `matching`
  - **핵심 상태 전이는 구현되어 있음**
  - 하지만 **실시간 매칭 완료 UX와 모바일 오프라인 복귀 UX는 아직 미완성**

- `chat`
  - **기본 채팅 API와 STOMP 실시간 구조는 네이티브 앱과 연동 가능한 수준**
  - 다만 **운영 안정성(리스너 정리, Redis 의존도, 테스트 부재)**은 보강이 필요함

- 전체
  - 지금 상태는 **“서버 도메인 모델/기본 API는 있음”**
  - 하지만 **“과팅 확정 UX를 앱에서 매끄럽게 완성하는 단계”까지는 아직 한 단계 남아 있음**
