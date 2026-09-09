# 1. 결론

**판정: NOT_READY — 중요한 알림을 안정적으로 제공한다고 판단하고 일반 사용자에게 배포하기에는 선행 수정과 실기기 검증이 필요합니다.** FCM, 알림 DB, outbox, 자동 재시도 등 기본 구조는 존재합니다. 그러나 로그아웃 후 푸시 등록이 유지되고, 이미 적재한 outbox가 계정 변경·탈퇴 뒤에도 과거 토큰으로 발송될 수 있습니다. 그룹 매칭 성사 알림은 일반 FCM outbox를 우회하며 저장소 안에는 별도 Redis 발송 이벤트의 소비자가 없습니다. 채팅은 STOMP 구독을 실제 열람으로 간주하여 백그라운드 상태에서 푸시 생략과 잘못된 읽음 처리가 함께 발생할 수 있습니다. 모바일 앱 소스가 없어 권한·채널·수신 콜백·종료 상태 클릭까지의 정상 동작은 확정할 수 없습니다. 이는 모든 푸시가 실패한다는 판정이 아니라, 현재 확인 가능한 구현으로 배포 기준을 충족하지 못한다는 판정입니다.

| 반드시 답해야 하는 질문 | 결론 |
| --- | --- |
| 앱 실행 중 알림이 정상적으로 오는가? | 채팅·팀방 STOMP 경로와 알림함 REST는 존재. 전체 알림함 자동 갱신·foreground FCM 표시는 앱 구현 미확인. 안정 수신 확정 불가. |
| 백그라운드에서 정상적으로 오는가? | 일반 알림은 조건 충족 시 OS 표시 가능한 혼합 payload. 그룹 매칭 성사 경로 단절, 채팅 구독에 의한 발송 생략, Android NORMAL 우선순위 때문에 안정 수신 보장 불가. |
| 완전히 종료되어도 오는가? | 일반적인 프로세스 종료와 사용자 강제 중지는 구분해야 함. 일반 종료는 OS 수신 경로가 가능하지만 앱 설정 미검증. Android 설정의 강제 중지 이후 재실행 전 수신을 보장하면 안 됨. |
| 로그아웃·계정 변경 시 오발송 가능성이 없는가? | **있음.** 로그아웃만 수행하면 등록 유지. 같은 토큰의 소유권 이전이 성공해도 이전 outbox는 남아 있음. |
| 배포 전 무엇을 고쳐야 하는가? | 로그아웃·계정 전환과 발송 대상 재검증, 그룹 매칭 성사 FCM 연결, 채팅 열람 상태 판정, 오류 분류, 배포 설정 검증. 모바일 권한·수신·클릭 경로는 실제 배포 빌드로 통과해야 함. |

근거: [로그아웃][S6], [발송 워커][S2], [그룹 매칭 알림][S13], [Redis 전달][S14], [채팅 푸시][S10].

**분석 범위와 신뢰도**

- 분석일: 2026-09-09, Asia/Seoul.
- GitHub 기본 브랜치: develop. 확인 시점 커밋: [2f7c360d0077b562f678fc74708630ad5e97b08e](https://github.com/jaemin-devlog/AirConnect/commit/2f7c360d0077b562f678fc74708630ad5e97b08e).
- 로컬: codex/figma-tools, HEAD 749ac531e5d2c9e5f85f3077b17fd2b6e863c2b9. GitHub 트리와 소스·설정 등 514개 파일의 blob을 대조. 차이가 있는 알림 outbox 엔티티·저장소 및 관리자 재시도 서비스는 GitHub 버전을 추가로 읽어 반영했습니다. 본문 코드 링크는 GitHub 확인 커밋에 고정되어 있습니다.
- 모바일 앱, 실행 중인 운영 서버의 환경변수·DB·Firebase Console·APNs 인증 설정·배포 바이너리·실제 스마트폰은 확인하지 않았습니다. 별도 외부 푸시 소비자의 존재도 확인할 수 없습니다.
- 기존 알림·Redis 매칭 푸시·채팅 읽음 테스트 실행을 시도했으나 Gradle 배포본 다운로드 제한, 이후 캐시의 configuration-processor JAR 접근 오류로 테스트 실행까지 완료하지 못했습니다. 테스트 통과나 실제 수신 성공으로 간주하지 않았습니다.
- 소스·배포 설정을 수정하거나 실제 사용자에게 알림을 발송하지 않았습니다. 이 문서의 PASS는 명시한 범위의 **코드상 조건부 판단**이며 실기기 테스트 실적이 아닙니다.
- “확정”은 실행 경로/분기 존재에 대한 정적 확인입니다. 실제 개인정보 사고 발생 사실, 발생 빈도, OS별 수신율을 의미하지 않습니다.

# 2. 현재 알림 아키텍처

## 2.1 실제 기술과 지원 범위

| 점검 항목 | 확인 결과 |
| --- | --- |
| 저장소 기술 | Java 17, Spring Boot 3.4.3, JPA, Spring Security/JWT, Redis, WebSocket/STOMP, Firebase Admin SDK 9.7.0. [build.gradle][S38] |
| 모바일 기술 스택 | **확정 불가.** Flutter/React Native/Android Native/iOS Native 프로젝트가 이 저장소에 없음. 디자인 도구는 모바일 런타임 근거가 아님. |
| Android/iOS | 서버의 PushPlatform과 FCM/APNs 메시지 설정에서 양쪽을 대상으로 함. 실제 앱 지원 버전·배포 여부는 미확인. |
| Firebase/FCM | 사용함. 자체 Spring 서버가 Firebase Admin SDK로 FCM 호출. [FirebasePushConfig][S25], [Sender][S3] |
| APNs | 직접 APNs HTTP 발송 구현은 없음. iOS도 FCM token으로 등록하고 Firebase가 APNs로 중계하는 구조. apnsToken은 저장되지만 이 서버의 실제 발송 대상은 pushToken. |
| 알림함 | notifications 테이블 + REST 조회/미읽음 개수/읽음/삭제 API. Firebase In-App Messaging 사용 근거 없음. |
| 실시간 앱 이벤트 | 채팅은 Redis Pub/Sub → STOMP; 채팅 목록과 팀방 상태는 SimpMessageSendingOperations. 전체 알림함 전용 실시간 발행은 발견하지 못함. |
| 발송 큐 | notification_outbox의 PENDING/PROCESSING/SENT/FAILED/SKIPPED. DB claim, 자동 재시도, 고아 PROCESSING 복구 있음. |
| 모바일 설정 파일 | AndroidManifest.xml, Info.plist, entitlements, google-services.json, GoogleService-Info.plist 없음. |
| 서버 Firebase 설정 | application.yml, application-dev.yml, FirebasePushConfig/Properties, docker-compose.yml의 외부 서비스 계정 마운트. |
| 앱 Lifecycle/백그라운드 | 모바일 Lifecycle·background handler 소스 없음. 서버 @Scheduled 작업은 존재하며 모바일 background handler와는 별개. |

## 2.2 일반 알림 전달 흐름

~~~mermaid
flowchart TD
    A["매칭 요청 또는 채팅 메시지"] --> B["MatchingService / ChatService"]
    B --> C["NotificationService.createAndEnqueue"]
    C --> D["사용자 알림 설정·quiet hours 판정"]
    D --> E["notifications 저장"]
    E --> I["NotificationController → REST 알림함"]
    E --> F["활성·권한 허용 PushDevice 조회"]
    F --> G["notification_outbox 저장: 토큰과 본문 복사"]
    G --> H["NotificationOutboxWorker.drain"]
    H --> J["NotificationOutboxService.claimNextBatch"]
    J --> K["FirebasePushNotificationSender.send"]
    K --> L["FCM: notification + data"]
    L --> M["Android OS"]
    L --> N["APNs → iOS"]
    M --> O["수신·표시·클릭·화면 이동: 앱 소스 미확인"]
    N --> O
~~~

실제 매칭 요청의 추적 지점은 다음과 같습니다.

| 단계 | 실제 클래스/함수와 데이터 |
| --- | --- |
| 도메인 이벤트 | MatchingService의 연결 요청 처리 → sendMatchRequestReceivedNotification(). [코드][S22] |
| 알림 작성 | 대상 userId, requesterNickname, connectionId, deeplink=airconnect://matching/requests, dedupeKey 작성 |
| 원본/큐 | NotificationService.createAndEnqueue() → createInternal() → notifications 저장 → 기기별 NotificationOutbox.create() |
| 기기 연결 | PushDeviceService.findPushableDevices(userId), userId + deviceId, provider + pushToken 유일성 |
| 발송 예약 | NotificationOutboxService.claimNextBatch(): PENDING을 SKIP LOCKED로 선택하고 PROCESSING 전환 |
| FCM 요청 | FirebasePushNotificationSender.send() → buildMessage() → FirebaseMessaging.send() |
| 응답 기록 | 워커가 SENT 또는 재시도/실패/무효 토큰 처리. SENT는 FCM 접수 성공이며 스마트폰 표시 확인이 아님 |
| 스마트폰 | 실제 SDK 등록·콜백·권한·채널 확인 불가 |
| 클릭 | data의 notificationId, notificationType, deeplink를 앱이 처리해야 함. 이 서버는 화면 이동을 실행하지 않음 |

채팅은 ChatService.sendMessage() → saveAndPublishMessage() → publishChatMessageNotifications()가 같은 일반 큐를 사용합니다. 하지만 해당 방을 보고 있다고 판정되면 알림 생성 자체를 생략합니다. [채팅 저장·발행][S12], [푸시 생략 분기][S10]

## 2.3 그룹 매칭 성사는 별도 경로

~~~mermaid
flowchart TD
    A["GMatchingService: 최종 그룹방 생성"] --> B["GMatchingEventPublisher.publishMatched → STOMP"]
    A --> C["notifyGroupMatched → NotificationService.create"]
    C --> D["notifications 저장: 일반 outbox 생성 없음"]
    A --> E["GRedisMatchingPushService.notifyMatched"]
    E --> F["RefreshTokenRepository: 사용자 deviceId 조회"]
    F --> G["Redis Pub/Sub push:dispatch:matching"]
    G --> H["소비자 및 FCM 연결 구현: 저장소에서 발견되지 않음"]
~~~

GROUP_MATCHED 호출의 enqueuePush 인자는 false입니다. 별도 서비스는 FCM token이 아니라 **인증 refresh token에 저장된 deviceId**를 가져와 Redis에 JSON을 publish합니다. Redis subscriber 수를 확인하지 않고, 발송 큐에도 보관하지 않습니다. docker-compose에도 별도 소비자 서비스가 없습니다. **저장소 구성만 배포하면 그룹 매칭 성사 OS 푸시 경로는 완결되지 않습니다.** 외부 소비자가 실제로 있다면 그 소스·배포·권한 정책·재시도까지 추가 검토해야 합니다. [호출][S44], [알림 생성][S13], [Redis 발행][S14], [Redis 설정][S39]

# 3. 인앱 알림 분석

| 항목 | 현재 동작 | 위험과 사용자 영향 |
| --- | --- | --- |
| 알림 목록 | GET /api/v1/notifications. userId 기준 ID 내림차순 커서 조회, 기본 20/최대 100개 | 서버에는 조회할 때 최신 데이터를 반환하는 구현이 있음. 자동 호출 주기·화면 재진입 시 갱신은 앱 미확인 |
| 실시간 갱신 | 채팅방 /sub/chat/room/{id}, 채팅목록 /sub/chat/list/{userId}, 팀방 /sub/matching/team-room/{id} | 전체 알림함 새 알림/읽음 이벤트 전용 STOMP/SSE는 발견하지 못함. 앱의 FCM 수신 후 재조회 또는 polling 구현이 필요하지만 존재 여부 미확인 |
| 채팅 알림함 | CHAT_MESSAGE_RECEIVED를 목록·미읽음 개수에서 제외 | 채팅 DB와 채팅 목록이 복구 경로. 푸시 누락을 알림함에서 찾을 수 있다고 안내하면 잘못됨 |
| 읽음/삭제 | readAt와 deletedAt. 단건은 id + userId + deletedAt 조건, 전체 읽음은 사용자 범위 갱신 | 다른 계정 ID로 읽기·삭제할 수 있는 경로는 해당 서비스에서 확인되지 않음. 동시 생성/읽음·삭제 경쟁은 DB 통합 검증 필요 |
| 다른 기기의 읽음 | DB 상태 공유 → 재조회하면 반영 | 알림함 읽음 상태를 다른 기기에 실시간 전파하는 구현은 없음. 화면/배지의 즉시 동기화는 보장 불가 |
| 푸시 OPENED | /push/events에 추적 이벤트 저장 | Notification.markRead()를 호출하지 않음. 클릭만으로 읽음이 된다고 가정하면 계속 미읽음 |
| 중복 | (userId, dedupeKey) DB unique, 기존 알림이면 outbox 재생성 생략 | null dedupeKey를 사용하는 팀원 활동 등은 공통 중복 방지 대상이 아님. FCM/STOMP 수신 후 화면에 추가하는 방식은 앱의 notificationId/messageId 기반 병합 필요 |
| 인앱 비활성화 | 원본을 저장한 뒤 softDelete로 숨김 | 나중에 인앱 설정을 켜도 과거 숨긴 알림이 자동 복원되지 않음 |
| 로그아웃 | 서버 목록은 인증 사용자 기준 | 앱 캐시·상태 저장소·미읽음 배지 초기화 미확인. 잔존 STOMP 연결도 확인 필요 |
| 재연결 | REST 데이터는 남음, Pub/Sub/STOMP 이벤트 자체의 replay 없음 | 네트워크 복구 후 최신 목록·메시지를 재조회하지 않으면 갱신 누락 |

근거: [NotificationInboxService][S8], [NotificationController][S21], [설정 정책][S9], [PushEventService][S19], [STOMP 구성][S23], [RedisSubscriber][S24].

**특히 잘못된 채팅 읽음 판정이 푸시 누락을 숨길 수 있습니다.** isUserViewingRoom()은 Redis에 동일 userId의 방 구독 세션이 있으면 true를 반환합니다. 앱 foreground·현재 화면·실제 메시지 표시 확인은 입력으로 받지 않습니다. 이 판정을 푸시 생략뿐 아니라 applyImmediateReadForPersonalRoom()/applyImmediateReadForGroupRoom()에서도 사용합니다. 앱이 백그라운드인데 구독이 남아 있으면 “푸시 없음 + 이미 읽음”이 동시에 가능합니다. [열람 판정][S11], [자동 읽음][S43]

로그인 세션 키 TTL은 24시간이고 subscribe 해시·방 세션 집합에는 별도 짧은 열람 lease가 없습니다. UNSUBSCRIBE/DISCONNECT 정리 코드는 있으므로 **항상 24시간 누락된다는 뜻은 아닙니다.** 정상 해제가 지연되거나 서버가 재시작된 경우, 혹은 앱이 백그라운드에서도 구독을 유지하는 경우가 검증 대상입니다. [세션 저장][S48], [해제 처리][S36]

# 4. 앱 외부 Push 알림 분석

## 4.1 Payload

실제 발송기는 항상 setNotification(title, body)를 넣고 data를 추가합니다. 현재 일반 경로는 **notification + data 혼합 메시지**입니다. data-only 발송 구현이 아닙니다. [Sender.buildMessage()][S3]

공통 data에는 notificationId, type, notificationType, title, body, deeplink, sentAt이 들어갑니다. 채팅은 chatRoomId/messageId/senderUserId/senderNickname/messagePreview 등을 추가합니다. FCM data는 문자열 맵이므로 숫자 ID도 문자열로 수신합니다. type=CHAT_MESSAGE/NOTICE/SYSTEM은 대분류이고 실제 이벤트는 notificationType입니다. 매칭 이벤트 대부분은 type=SYSTEM이므로 앱이 type만 보고 상세 분기를 끝내면 잘못된 화면으로 갈 수 있습니다. [payload 정규화][S47]

| 상태 | 일반 FCM 혼합 메시지 예상 동작 | AirConnect에서의 판정 |
| --- | --- | --- |
| Foreground | Android는 onMessageReceived에 전달, 자동 OS 표시를 기대하면 안 됨. iOS는 willPresent 처리와 presentation 선택 필요 | 서버 payload 존재. 앱 콜백·인앱 표시·로컬 알림 구현 미확인 → 위험 |
| Background | Android는 OS 알림 표시, data는 알림 클릭 후 launcher Intent extras로 전달. iOS는 APNs alert 경로 | 유효한 토큰·설정·권한 전제의 수신 가능. 그룹 성사 경로와 채팅 발송 생략 결함 때문에 전체 PASS 아님 |
| 최근 앱 목록에만 존재 | 목록에 있다는 사실만으로 프로세스·연결 상태를 알 수 없음 | “background” 또는 “일반 프로세스 종료”로 다시 구분해야 함 |
| OS에 의한 process 종료 | 앱 프로세스 상주 없이 OS 푸시 수신이 가능한 구조 | cold start 때 클릭 data 보존과 로그인/라우터 준비 순서는 미확인 |
| Android 최근 앱 화면에서 제거 | 설정의 강제 중지와 다른 동작. 기종별 제한 차이 확인 필요 | 실기기 테스트 필요 |
| Android 설정에서 강제 중지 | 재실행 전 FCM 수신 재개를 기대할 수 없음 | OS 제한. 이를 서버 재시도로 우회할 수 없음 |
| iOS 사용자가 앱 종료 | alert 표시와 앱의 백그라운드 코드 실행을 구분해야 함 | silent callback/백그라운드 실행 보장 불가. 현재 alert payload의 실제 표시·클릭은 배포 빌드로 확인 |
| 재부팅 후 | 등록 정보·네트워크·첫 잠금 해제 여부가 영향 | 직접 부팅 전 수신 설정 미확인. 재부팅 후 잠금 상태와 첫 해제 후를 분리 |
| 장시간 미사용 | 서버는 lastSeenAt/lastTokenRefreshedAt으로 일반 발송을 제한하지 않음 | stale token 유지. 그룹 Redis 경로는 refresh token TTL 30일에 종속 |
| Doze/절전/배터리 최적화 | Android NORMAL은 지연 가능 | 채팅에 NORMAL을 명시해 즉시성 위험. 다른 일반 중요 알림은 HIGH |
| 끊김→재연결 | FCM 보관·만료·collapse에 따라 일부가 나중에 전달될 수 있음 | 모든 이벤트별 배너 수신은 보장 불가. REST로 실제 상태를 복구해야 함 |
| Wi-Fi→LTE/5G | FCM 및 STOMP 재연결 시점이 다를 수 있음 | 오래 남은 채팅 구독으로 푸시까지 생략되는 결합 위험 |

OS 동작 근거: [Android 수신 규칙](https://firebase.google.com/docs/cloud-messaging/android/receive-messages), [Apple 수신 처리](https://firebase.google.com/docs/cloud-messaging/ios/receive-messages), [FCM Android 강제 중지 조건](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages), [Android 우선순위](https://firebase.google.com/docs/cloud-messaging/android-message-priority). 마지막 강제 중지 문서는 Flutter 예제 문서의 OS 조건만 참고했으며 AirConnect를 Flutter로 판단한 것이 아닙니다.

## 4.2 중복 표시·유실에 관한 구분

- background에서 notification을 OS가 표시하는 것과 앱이 임의로 로컬 알림을 추가하는 것은 별도입니다. 앱 코드가 없어 “현재 이중 표시 버그가 있다”고 확정할 수 없습니다.
- Android background 혼합 메시지의 data가 즉시 onMessageReceived로 오는 것을 전제로 알림함/수신 이벤트를 갱신하면 동작하지 않습니다.
- 서버 data.deeplink는 자동 navigation 명령이 아닙니다. Android clickAction, 실제 Intent 처리, iOS notification response 처리 여부는 확인되지 않았습니다.
- 서버에는 background handler·initial message 처리 소스가 없지만, **백엔드 저장소에 없다는 이유로 실제 앱에서 누락됐다고 단정하지 않습니다.**
- APNs 설정은 priority=10, sound=default이며 content-available이나 badge를 설정하지 않습니다. 따라서 silent background sync 및 iOS 아이콘 숫자 배지 동기화는 이 payload만으로 제공되지 않습니다. [발송 코드][S3]

# 5. Critical / High 문제

| 중요도 / ID | 문제 | 발생 조건 | 사용자 영향 | 코드 근거 | 해결 방향 |
| --- | --- | --- | --- | --- | --- |
| **P0 / N01** | 로그아웃이 푸시 등록을 해제하지 않음 | /auth/logout만 성공하고 별도 push device DELETE를 호출하지 않음 | 로그아웃 후 이전 계정의 닉네임·메시지 내용이 기기에 표시될 수 있음 | AuthService.logout() 175–183행은 refresh token 삭제만 수행. [코드][S6] | 서버 로그아웃에서 해당 기기의 푸시 소유권/활성 상태와 대기 작업 정리를 연계. 오프라인 로그아웃도 설계 |
| **P0 / N02** | 큐에 복사한 과거 토큰으로 계정 변경·탈퇴 후에도 발송 | A 알림 PENDING/재시도 → 기기 해제/B 등록/탈퇴 → 워커 실행 | 같은 스마트폰의 B가 A의 알림을 보는 경로. 이미 claim한 작업도 해당 | NotificationService가 토큰 복사, Worker는 바로 send(), Sender는 targetToken 사용. [생성][S1], [워커][S2], [발송][S3] | 발송 직전에 현재 소유자·기기 세대·활성/권한·사용자 상태 검증. 해제/전환 시 큐 무효화 및 경합 통제 |
| **P1 / N03** | 그룹 매칭 성사 OS 푸시 경로 미완결 | 현재 저장소/Compose 구성으로 그룹 매칭 완료 | 핵심 성사 알림이 앱 밖에서 오지 않음 | enqueuePush=false → create(); 별도 Redis publish만 존재. [알림][S13], [별도 경로][S14] | 기존 FCM outbox로 연결. 외부 소비자를 쓰고 있다면 그 구현·배포를 증명하고 동일 정책/내구성 적용 |
| **P1 / N04** | 방 구독을 현재 열람으로 간주해 푸시 생략·자동 읽음 | 채팅방 구독 후 background/네트워크 단절, 오래 남은 세션, 다른 기기에 열린 방 | 중요한 채팅 미통지, 실제로 보지 않은 메시지 읽음 | isUserViewingRoom(), publishChatMessageNotifications(), applyImmediateRead*. [판정][S11], [발송][S10], [읽음][S43] | foreground/화면 표시 상태와 짧은 lease를 명시. 실제 표시/읽음 확인과 구독 분리. 다중 기기 정책 결정 |
| **P1 / N05** | INVALID_ARGUMENT 전체를 토큰 무효로 분류 | FCM이 payload 문제를 INVALID_ARGUMENT로 응답 | 정상 기기의 토큰을 release하여 이후 모든 알림까지 중단 가능 | isInvalidTokenFailure() 232행 이후. [코드][S45] | payload 오류와 토큰 오류를 분리. 바이트 크기/예약키 등 사전 검증 및 오류 상세에 따른 제한적 토큰 폐기 |
| **P1 / N06** | FCM 꺼짐 + worker 켜짐이면 가짜 성공으로 소진 | 운영 환경변수/프로필 불일치 | 실제 발송 없이 SENT 기록, 장애 탐지 혼란 | LoggingPushNotificationSender가 simulated-ID 성공 반환. [코드][S26], [설정][S27] | 운영 프로필에서는 simulated sender 금지, 설정 불일치 시작 실패 또는 명확한 비발송 상태. 실제 기기 canary |
| **P1 / N07** | 모바일 수신·클릭에 대한 배포 증거 없음 | Android/iOS 일반 사용자 배포 판단 시 | 권한/채널/cold start 실패를 놓칠 수 있음 | GitHub 전체 트리에 모바일 프로젝트·설정 없음 | **확정 결함이 아닌 출시 검증 차단 항목.** 앱 소스와 release/TestFlight 빌드에서 11–12절 검증 |

N01/N02의 P0는 실제 개인정보 노출이 가능한 경로를 우선 차단하자는 의미입니다. 이미 운영에서 사고가 발생했다고 주장하는 것은 아닙니다. 특히 N02는 클라이언트가 별도 기기 해제를 올바르게 호출하더라도 서버 큐가 남아 있다는 문제입니다.

Firebase 공식 문서는 INVALID_ARGUMENT가 payload 오류일 수도 있으므로 메시지가 유효함을 확인해야 토큰 무효로 해석할 수 있다고 설명합니다. 현재 코드는 그 구분이 없습니다. [Firebase 토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens)

# 6. Medium / Low 문제

| 중요도 / ID | 문제 | 발생 조건 | 사용자 영향 | 코드 근거 | 해결 방향 |
| --- | --- | --- | --- | --- | --- |
| P2 / N08 | Android 채팅 NORMAL 우선순위 | 화면 꺼짐·Doze | 메시지 알림 지연 | buildAndroidConfig() 124–136행. [코드][S46] | 시간 민감한 채팅의 전달 우선순위 검토. 채널 importance와 분리해 실기기 비교 |
| P2 / N09 | 채팅 coalescing이 끝없이 연기될 수 있음 | 같은 기기/방에 2초보다 짧은 간격으로 새 메시지 지속 | 스트림이 끊길 때까지 첫 알림 발송도 미뤄질 수 있음 | 매번 nextAttemptAt=now+2초로 갱신. [NotificationService][S1], [Outbox][S17] | 최초 대기 시작부터 최대 지연 한도 설정. 합침과 지연 상한을 별개로 정의 |
| P2 / N10 | 큐 만료·발송 직전 정책 재평가 없음 | 생성 후 권한/quiet hours/알림 설정 변경, 오래된 실패 재시도 | 원치 않는 알림, 끝난 요청/방 알림 | 설정은 생성 시 평가, 워커는 대상/내용 재검증 없음. [정책][S9], [워커][S2] | expiresAt와 발송 시 정책 재검증. quiet hours는 생략/이월 정책 명시 |
| P2 / N11 | 재시도·claim 복구에서 중복 가능 | FCM 성공 직후 DB 기록 실패, 처리 중 5분 lease 만료 | 같은 알림 재등장, 여러 워커가 같은 작업 처리 가능 | claim 복구와 markSent에 claim 소유권/version 검증 없음. [복구][S16], [상태 변경][S17] | claim 소유권/세대 검증, 적절한 timeout, notificationId 기반 기기 중복 처리 |
| P2 / N12 | 단일 순차 발송과 스케줄러 병목 | FCM 지연 또는 대량 알림 | 큐 지연, 다른 scheduled 작업 지연 가능 | 100개 batch를 for문으로 동기 send. 별도 scheduler/pool 설정 없음. [워커][S2] | 기존 DB 큐 유지, 발송 전용 scheduler와 제한된 동시성, backlog/지연 측정 |
| P2 / N13 | 알림함 즉시 동기화 계약 없음 | 화면을 열린 채 유지하거나 다른 기기에서 읽음 | 알림 목록·미읽음 숫자가 오래됨 | REST만 제공; 일반 알림함 broadcast 없음. [API][S21] | foreground 진입/수신/재접속 시 재조회, 필요 시 가벼운 polling 또는 갱신 이벤트 |
| P2 / N14 | push event 소유권·중복 검증 없음 | 로그인 사용자가 임의 notificationId/deviceId로 RECEIVED/OPENED 반복 요청 | 수신율/열람율 데이터 오염, 잘못된 객체 참조 | PushEventService.create()는 숫자 변환 후 바로 저장. [코드][S19] | 원본/수신자/기기 소유권 확인, 이벤트 유일성 또는 dedupe, 호출 제한 |
| P2 / N15 | STOMP를 DB 커밋 전에 발행 | 이후 알림 저장/DB 커밋 실패 | DB에 없는 메시지·완료 상태가 잠깐 노출, 클릭 직후 상태 불일치 | saveAndPublishMessage(), 그룹 완료 처리. [채팅][S12], [그룹][S44] | after-commit 발행 또는 내구성 이벤트. REST 재조회로 상태 수렴 |
| P2 / N16 | 토큰 소유권 이전·기본 설정 생성의 동시성 검증 부족 | 동시 등록/refresh, 동일 사용자의 첫 알림 동시 생성 | unique 충돌, 요청 실패/트랜잭션 rollback 가능 | 조회 후 저장, 해당 경로에 명시적 락/충돌 재시도 없음. [등록][S4], [정책][S9] | 실제 MySQL에서 동시 요청 검증, 원자적 upsert/충돌 재시도 |
| P2 / N17 | 잠금화면 메시지 미리보기 제한 없음 | 사용자가 잠금화면 미리보기 허용 | 닉네임·채팅 앞 100자 노출 | ChatService.summarizeChatPushPreview(), title/body/data. [코드][S10] | 서비스 개인정보 요구에 맞게 일반 문구·미리보기 옵션 적용 |
| P3 / N18 | sentAt·상태 명칭이 실제 의미와 다름 | 큐 지연, 권한 꺼짐, 계정 디바이스 해제 | 시간/수신 상태를 잘못 안내 | sentAt은 enqueue 시각, PushDeviceResponse.pushEnabled는 권한 Boolean만 반환. [payload][S47], [응답][S37] | enqueuedAt/acceptedAt/displayedAt 구분, 활성/설정 포함한 표시 상태 제공 |

N08은 OS 결함이 아니라 시간 민감도를 낮게 지정한 현재 코드의 선택입니다. N17도 항상 개인정보 사고라는 뜻은 아니며, OS 미리보기 설정과 제품의 공개 범위 결정이 함께 작용합니다.

**FCM collapse 설정 주의:** 서버는 채팅방별 collapseKey와 Android tag를 설정합니다. tag는 이미 표시된 같은 태그 알림을 대체하는 정책입니다. FCM 공식 문서는 notification 메시지가 collapsible이며 collapse_key를 무시한다고 설명하므로, 현재 혼합 메시지에서 “방별 FCM 큐가 분리되고 모든 방의 마지막 알림이 반드시 보존된다”고 판단해서는 안 됩니다. 서버의 2초 합침·FCM 보관 합침·OS tag 대체를 각각 관찰해야 합니다. [발송 설정][S46], [FCM collapse 규칙](https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types)

# 7. FCM/APNs Token Lifecycle 분석

| 단계 | 현재 구현 | 판단 |
| --- | --- | --- |
| 최초 발급 | SDK가 수행할 부분; 앱 코드 없음 | token 발급 시점·실패 재시도 미확인 |
| 서버 등록 | POST /api/v1/push/devices, 인증 userId + 요청 deviceId | 등록 API 존재. provider는 FCM만 허용 |
| 로그인 연결 | AuthService와 PushDeviceService 분리 | 로그인만으로 FCM 등록되지 않음. 앱이 인증 완료 후 별도 POST 해야 함 |
| 갱신 | 같은 userId/deviceId이면 refreshToken()으로 token·권한·메타데이터 변경, active=true | 서버 수용 가능. 앱 callback→업로드 성공 보장은 미확인 |
| 로그아웃 | refresh token만 삭제 | push_devices는 계속 활성. N01 |
| 동일 토큰으로 계정 변경 | 기존 provider/token 소유자의 토큰을 released:...로 바꾸고 비활성화 | 순차 등록이 성공하면 새로운 발송의 중복 소유 방지. 과거 outbox는 별개. N02 |
| 계정 변경과 토큰 변경 동시 발생 | 소유권 이전 검색은 새 pushToken 기준 | 예전 A 토큰과 B 새 토큰이 다르면 A 행을 deviceId만으로 찾아 해제하지 않음. 이전 token의 실제 유효 여부·기기 해제 호출에 따라 위험 |
| 재설치 | 새로운 deviceId면 새 행, 같으면 기존 행 갱신 | 이전 행이 즉시 삭제되는 경로 없음. 앱 삭제는 서버에 자동 로그아웃을 보장하지 않음 |
| 스마트폰 변경 | 기기별 행 추가 | 예전 휴대폰도 활성·권한=true이면 계속 수신할 수 있음. 사용자가 원격 해제할 API는 있음 |
| 동일 계정 여러 기기 | userId로 모든 활성·권한=true 기기 조회하여 기기별 outbox | 일반 경로 지원. 단 채팅은 어느 한 세션이 방을 보고 있으면 사용자 전체에 생성 생략 |
| 한 기기 여러 계정 | token은 provider별 unique, user/device는 별도 unique | 한 token의 동시 다중 계정 소유는 제한하지만 계정 전환 프로토콜 완결성은 부족 |
| 만료·오래된 token | lastSeenAt/lastTokenRefreshedAt 저장, 별도 stale 정리/필터 미발견 | 오래된 기기 비용·오류 누적. 모바일 정기 재등록과 서버 관리 정책 필요 |
| UNREGISTERED | 워커가 deactivateInvalidToken() → releaseTokenOwnership(), SKIPPED | 일반 처리 방향은 있음. 해당 token으로 이미 대기 중인 다른 outbox는 그대로 남음 |
| INVALID_ARGUMENT | 무조건 invalid 처리 | payload 오류에도 정상 token 폐기 가능. N05 |
| SENDER_ID_MISMATCH | invalid 처리 | 프로젝트 설정 불일치도 조사해야 함. 설정 오류가 대량 기기 해제로 이어질 수 있음 |
| 임시 FCM 오류 | INTERNAL/UNAVAILABLE/QUOTA_EXCEEDED 등 재시도 | 애플리케이션 자동 시도 최대 3회. 영구 실패/수동 복구 구분 필요 |
| iOS APNs token | 선택적으로 apnsToken 저장 | 실제 발송은 FCM token. 서버에 APNs token을 저장하는 것만으로 SDK의 APNs↔FCM 연결이 완성되지 않음 |

근거: [PushDeviceController][S20], [PushDeviceService][S4], [PushDevice][S5], [Sender][S3], [Worker][S2].

토큰 자체가 수신 권한 허용을 뜻하지는 않습니다. 권한=false를 서버가 전달받으면 일반 새 알림의 outbox를 만들지 않지만, 클라이언트가 시스템 설정 변경을 보고하지 않으면 서버 정보는 낡습니다. false→true 갱신 후 **이후 알림**은 재개할 수 있으나, 권한 거부 중 생성하지 않은 outbox를 자동으로 소급 생성하지 않습니다.

장기간 미사용과 인증 만료도 다릅니다. 일반 FCM 경로는 로그인 refresh token의 유효성과 직접 연결되어 있지 않습니다. 반면 그룹 Redis 경로는 30일 TTL의 RefreshTokenRepository에서 기기를 찾으므로, 향후 외부 소비자를 추가하더라도 인증 세션 만료만으로 그룹 푸시 대상에서 빠질 수 있습니다. [그룹 대상 조회][S14], [인증 토큰 TTL][S33]

# 8. 로그인 / 로그아웃 / 계정 변경 위험

## 8.1 재현 가능한 서버 경로

**상황 A — 로그아웃 후 새 알림**

1. A가 token T를 등록하여 active=true, permission=true가 됨.
2. A가 /auth/logout 호출. refresh token만 삭제.
3. A에게 새 매칭 요청이 발생.
4. findPushableDevices(A)는 여전히 T를 반환.
5. 워커가 A의 알림을 T로 보냄.

앱이 별도 DELETE /push/devices/{deviceId}를 성공시킨다면 이후 새 outbox는 막을 수 있습니다. 하지만 서버 로그아웃 자체의 계약은 이를 보장하지 않습니다. 네트워크 단절·API 호출 순서·오프라인 로그아웃도 시험해야 합니다.

**상황 B — B 등록이 정상 성공해도 남는 과거 알림**

1. A에게 알림 발생 → outbox에 userId=A, targetToken=T를 저장.
2. A의 기기를 비활성화하거나 B가 같은 T를 등록하여 A 소유권을 해제.
3. B가 현재 스마트폰을 사용.
4. 워커는 outbox.targetToken=T로 그대로 발송.
5. notification 본문이 OS에 자동 표시되면 앱에서 B/A를 검사하기 전에 내용이 노출될 수 있음.

이는 “토큰 소유권 이전 코드가 전혀 없다”는 문제가 아닙니다. **새 등록의 소유권 이전과 과거 발송 작업의 무효화가 연결되어 있지 않다는 문제**입니다. [소유권 이전][S4], [발송][S2]

**상황 C — 탈퇴**

UserService.deleteAccount()는 active 기기들의 토큰 소유권을 해제하므로 단순 탈퇴 처리 자체에 방어가 있습니다. 하지만 일반 탈퇴 경로는 notification_outbox를 삭제하지 않습니다. 관리자 hard purge에는 outbox 삭제가 있지만 일반 탈퇴와 동일하지 않으며, 워커가 이미 메모리로 가져온 작업까지 취소한다고 볼 수 없습니다. [일반 탈퇴][S7], [관리자 purge][S42]

## 8.2 보안/개인정보 판단

| 점검 | 결과 |
| --- | --- |
| 알림 조회 owner 검증 | 인증 userId 조건으로 조회. 단건 읽음·삭제도 id+userId로 제한. notificationId만 바꿔 타인 알림 내용을 읽는 경로는 확인되지 않음 |
| 푸시 API 인증 | /api/v1/push/** 및 /v1/push/**는 anyRequest().authenticated(). userId는 CurrentUserId에서 가져옴 |
| 탈퇴 계정 API 접근 | CurrentUserIdArgumentResolver가 DELETED/SUSPENDED를 거절. 이 검사는 비동기 발송 워커에는 적용되지 않음 |
| push event owner 검증 | 누락. 임의 notificationId로 이벤트 적재 가능. 현재 분석상 내용 조회 유출이 아니라 객체 참조/측정 무결성 문제 |
| 로그아웃 후 JWT/세션 | logout에서 access token 즉시 폐기·STOMP 연결 정리는 발견되지 않음. 앱이 토큰/구독/캐시를 정리하는지 별도 확인 |
| 잠금화면 | 채팅 닉네임과 최대 100자 미리보기 포함. OS 표시 허용 범위에 따라 노출 |
| 기기 목록 응답 | 실제 pushToken/apnsToken 원문을 응답하지 않음. apnsTokenRegistered 여부만 제공 |
| 토큰 로그 | 알림 서비스의 정상 로그에서 token 원문 출력은 발견하지 못함. provider 오류 메시지는 그대로 로그/DB에 저장하므로 실제 오류 응답 마스킹 확인 필요 |
| deviceId 로그 | 일반 PushDeviceService는 마스킹. GRedisMatchingPushService는 debug/error에서 deviceId 원문 기록 |
| Firebase credential | 확인한 GitHub 트리에서 Firebase service-account JSON/private key 파일 미발견. 외부 파일 또는 ADC 사용, secrets/는 ignore, Compose read-only 마운트 |
| GitHub 과거 유출 여부 | 전체 커밋 이력·CI 로그·과거 이미지 레이어까지 검증한 것은 아님. “과거 유출도 없다”는 결론은 내릴 수 없음 |
| Apple 인증 자료 | Apple 로그인/IAP 관련 자료를 APNs push capability나 Firebase APNs Key 등록의 증거로 간주하지 않음 |

OWASP 관점에서는 N01/N02를 사용자 세션 종료와 민감정보 노출 경계 문제로, N14를 객체 수준 권한 검증 및 이벤트 무결성 문제로 볼 수 있습니다. 전체 OWASP 적합성 감사나 법적 판단을 수행한 것은 아닙니다.

근거: [SecurityConfig][S30], [CurrentUserId][S31], [Inbox owner 확인][S8], [이벤트 적재][S19], [응답][S37], [Firebase credential 로딩][S25], [ignore][S41].

# 9. Push 유실 / 중복 가능성

## 9.1 유실·실패 지점

| 구간 | 현재 방어 | 남는 위험 |
| --- | --- | --- |
| 도메인 이벤트→Notification 생성 | 매칭/채팅/그룹 서비스에서 호출 | 생성 전에 예외가 나고 호출자가 catch하면 재생성할 내구성 이벤트 없음 |
| Notification + outbox DB 저장 | 일반 경로는 동일 트랜잭션 참여 | DB 예외를 catch해도 rollback-only는 사라지지 않음. 알림 오류가 도메인 트랜잭션 전체 실패로 번질 수 있음 |
| DB commit→worker | DB에 PENDING 보관 | worker 꺼짐이면 적체. 설정은 실제 운영값 확인 필요 |
| FCM 호출 | worker에서 수행, 재시도 있음 | 3회 이후 FAILED. 장기 장애 자동 회복 범위가 제한됨 |
| FCM 성공→SENT 저장 | providerMessageId 저장 | FCM은 접수했는데 DB 기록 실패/프로세스 종료면 재발송 가능 |
| claim 후 서버 종료 | 5분 초과 PROCESSING을 60초 주기로 PENDING 복구 | 복구 지연. 살아 있는 느린 워커도 다른 인스턴스에 의해 회수될 수 있음 |
| FCM 접수→기기 수신 | FCM/OS가 담당 | 권한, 무효 token, 장기 오프라인, collapse, OS 제한. SENT로 실수신 확정 불가 |
| 기기 수신→앱 화면 | 앱 담당 | foreground 처리·초기 메시지·재조회 구현 미확인 |
| 그룹 성사 Redis publish | 별도 내구성 저장 없음 | 소비자 미존재/중단/Redis 장애 시 재생 경로 없음 |

NotificationService는 일반 도메인 트랜잭션과 함께 DB에 기록하므로 정상 rollback이면 Notification/outbox도 취소되고 worker가 그 미커밋 행을 보낼 수 없습니다. 따라서 일반 FCM 경로에서 “DB가 실패해도 항상 푸시부터 발송된다”고 판단하는 것은 잘못입니다. 반면 STOMP/Redis는 트랜잭션 중간에 외부 발행하며 rollback되지 않습니다. [일반 생성][S1], [채팅 순서][S12], [그룹 순서][S44]

## 9.2 재시도는 이미 구현되어 있음

- 애플리케이션 워커 기준 최대 3번 호출: 첫 실패 후 약 1분, 두 번째 실패 후 약 5분 뒤 다음 호출. 실제 대기는 scheduler/backlog에 따라 더 길어짐.
- nextAttemptAt()의 30분 분기는 존재하지만 현재 일반 MAX_ATTEMPTS=3 경로에서 통상 세 번째 실패 뒤 추가 자동 예약까지 진행하지 않음.
- SDK 내부 재시도 횟수와 이 애플리케이션 시도 횟수는 별개.
- 자체 jitter, Retry-After 반영, 오류별 장기 복구 정책은 없음.
- GitHub 버전에는 관리자 retryFailedOutbox()가 있음. FAILED만 재시도하고 현재 기기 owner/활성/권한/provider/token을 확인한 뒤 큐에 넣음. **수동 재시도도 없다고 판단하면 안 됩니다.** 그러나 검사 시점은 관리자 요청 시점이며 일반 워커의 발송 직전 검증을 대신하지 못함. [Worker][S2], [관리자 재시도][S32]

## 9.3 중복 방지의 한계

알림 (userId, dedupeKey), outbox (notificationId, pushDeviceId) 유일성이 있습니다. 이는 DB 중복 생성 방어이며, 동일 outbox를 FCM에 두 번 보내는 것을 막는 공급자 멱등 키는 아닙니다. 팀원 입장/퇴장/준비 변경 등은 null dedupeKey를 사용할 수 있습니다. 동시 unique 충돌을 같은 트랜잭션에서 catch한 뒤 조회하는 방식의 복구 가능성은 MySQL 통합 테스트가 필요합니다. [알림 엔티티][S35], [생성][S1], [Outbox][S17]

이미 읽은 다른 기기의 OS 알림을 취소하거나 배지를 갱신하는 push도 이 서버에는 없습니다. 서버 읽음 상태와 기기별 OS 알림 잔존 상태가 다를 수 있습니다.

## 9.4 100 / 1,000 / 10,000명 규모

사용자 수만으로 처리 용량을 확정할 수 없습니다. 필요한 값은 초당 이벤트 수 × 수신자 수 × 활성 기기 수이며, 채팅 합침/열람 생략을 반영해야 합니다.

| 규모 예시 | 현재 구조 판단 | 먼저 측정할 항목 |
| --- | --- | --- |
| 100명 | DB outbox 자체는 과도하지 않은 구성. 기본 기능·개인정보 경계 검증이 우선 | 실제 FCM 왕복 시간, 미수신/가짜 SENT, 오래된 PENDING |
| 1,000명 | 동시 대화/공지 burst에서 순차 발송 지연 가능 | 초당 생성량·처리량, enqueue→FCM 접수 p95/p99, DB lock/connection 대기 |
| 10,000명 | 가입자 수만으로 병목 확정 불가. 다수에게 동시 발송하면 단일 워커 처리 시간이 누적 | backlog 증가율, worker 수, 재시도 비율, 토큰 조회/알림 생성 query 수, 메모리/Redis 지연 |

일반 이벤트 HTTP/STOMP 요청이 직접 FCM API를 호출하지는 않습니다. 외부 FCM 대기는 worker에서 발생하며 claim/결과 DB 트랜잭션은 분리됩니다. 그러나 요청 중 기기별 outbox INSERT·설정 조회·채팅 Redis 작업은 동기적으로 수행됩니다. batch size=100은 **DB claim 크기**이며 FCM multicast/batch 전송이 아닙니다.

예를 들어 측정된 한 건 처리 시간이 0.1초라고 가정하면, 100건 순차 발송에 약 10초 + fixed delay 1초 + DB 비용이 들어갑니다. 10,000건을 한 worker로 처리하는 데 약 18분 이상이 걸릴 수 있습니다. 이는 예시 계산이며 실제 성능 측정 결과가 아닙니다.

토큰 조회에는 user_id,active 인덱스가 있습니다. 반면 Android 채팅 pending 조회는 push_device_id와 JSON_EXTRACT 조건을 사용하고 그 조합의 전용 인덱스는 엔티티에서 확인되지 않습니다. 채팅 목록 미읽음 계산은 수신 사용자별 쿼리를 반복합니다. JPA 관계 로딩에 따른 추가 N+1 여부와 정확한 비용은 SQL trace/EXPLAIN으로 확인해야 합니다. [PushDevice 인덱스][S5], [outbox 쿼리][S16], [채팅 조회·발행][S12]

현재 단계에서는 기존 DB 큐를 보완하는 것이 우선입니다. 전용 scheduler, 제한된 동시 발송, 적절한 인덱스, 실패/지연 경보를 검토하고, 트래픽 측정 없이 Kafka 도입을 전제할 이유는 없습니다. FCM quota는 실제 프로젝트 설정과 응답을 확인해야 하며 가입자 10,000명이라는 숫자만으로 초과 여부를 판단할 수 없습니다.

# 10. 알림 클릭 / Deep Link 분석

| 실제 이벤트 | 생성되는 경로/키 | 확인할 점 |
| --- | --- | --- |
| 1:1 매칭 요청/거절 | airconnect://matching/requests, connectionId | 요청 목록 재조회, 이미 처리된 요청 안내 |
| 1:1 매칭 수락 | airconnect://chat/rooms/{chatRoomId} | 방 접근 권한·로그인 확인 후 이동 |
| 채팅 | airconnect://chat/rooms/{roomId}, messageId | 채팅 REST 재조회, 삭제/나간 방 처리 |
| 그룹 성사 | airconnect://group-chat/final/{finalGroupRoomId}, finalChatRoomId | finalGroupRoomId와 실제 chatRoomId를 혼동하면 안 됨. 현재 일반 FCM 경로는 누락 |
| 팀방 활동 | airconnect://matching/team-rooms/{teamRoomId} | 팀방 종료/권한 소멸 처리 |
| 팀방 해산 | airconnect://matching/team-rooms | 사라진 상세 화면 대신 목록으로 가는 서버 경로 존재 |
| 좋아요 | 해당 알림 타입/producer를 발견하지 못함 | 구현되어 있다고 가정하지 않음 |
| 마일스톤·약속 리마인더 | enum/설정 분기는 있으나 main producer/scheduler 미발견 | 실제 생성 기능으로 간주하지 않음 |

근거: [매칭][S22], [채팅][S10], [그룹][S13], [타입][S34].

클릭 자체의 합격 조건은 다음과 같습니다.

| 조건 | 필요한 동작 | 코드상 확인 결과 |
| --- | --- | --- |
| 실행 중 클릭 | 대상 중복 push 방지, 현재 라우터에서 이동 | 모바일 코드 없음 |
| background 클릭 | OS가 전달한 data 보존 → 인증/라우터 준비 → 이동 | 서버에 deeplink 있음. 앱 처리 미확인 |
| terminated 클릭 | 초기 이벤트를 임시 보관하고 앱 초기화 후 한 번만 소비 | initial-message/launch Intent 처리 미확인 |
| 로그아웃 상태 | 로그인 전 민감 본문/방 데이터 표시 제한. 로그인 후 수신 대상 계정 일치 확인 | server payload에 공통 수신자 userId/계정 세대 없음. actorUserId·senderUserId는 수신자가 아님 |
| 삭제된 데이터 | 권한/존재 확인 후 “종료되었거나 접근할 수 없음” 안내 | ChatService는 방/참여자 검증 있음. 앱 오류 화면 미확인 |
| 연속 클릭 | 최신 사용자 의도에 따라 처리, 중복 route/비동기 경합 방지 | 미확인 |
| 알림 열람 | OPENED 이벤트와 /notifications/{id}/read의 의미 분리 | 서버 OPENED는 읽음 처리하지 않음 |

채팅은 findRoomOrThrow()/validateRoomAccess()가 없어지거나 접근 불가한 방을 거절합니다. 이것이 앱 crash 방지까지 보증하지는 않습니다. 서버가 오류를 반환했을 때 앱이 어떻게 처리하는지 실제 기기 테스트가 필요합니다. [ChatService][S10]

# 11. Android / iOS OS별 위험

## 11.1 Android 권한·채널

| 항목 | 서버에서 확인한 사실 | 앱에서 확인해야 할 항목 |
| --- | --- | --- |
| Android 13+ POST_NOTIFICATIONS | permission Boolean 등록·변경 API 있음 | Manifest 선언, runtime 요청 코드, 최초 요청 시점, 거부/재요청/설정 열기 UX |
| 권한 거부 | 서버 false이면 일반 신규 outbox 생성하지 않음 | 실제 OS 상태를 정확히 업로드하는가. background 복귀·설정 복귀마다 재확인하는가 |
| false→true | PATCH 권한 갱신 가능 | stale false가 남아 서버가 계속 생략하지 않는가 |
| true→false | 이미 생성된 outbox는 계속 send 가능 | OS가 표시 차단하더라도 서버 정책과 관측값이 어긋나는지 |
| Android 8+ channel | 채팅에 airconnect_chat_push 명시 | 동일 ID 채널 사전 생성, importance, sound, vibration |
| 일반 알림 channel | 서버가 명시하지 않음 | Manifest default_notification_channel_id와 fallback 채널 동작 |
| 기존 설치 사용자 | 서버 설정으로 기존 채널 설정을 바꿀 수 없음 | 과거 낮은 importance/무음 채널, 사용자 수정 설정의 유지·설정 안내 |
| priority | 채팅 NORMAL/default 표시 priority, 기타 중요 알림 HIGH | FCM 전달 우선순위와 채널 importance를 구분하여 시험 |

Android 13 이상 신규 설치의 알림은 기본적으로 꺼져 있으며 사용자 허용이 필요합니다. 요청 시점과 재요청 동작은 target SDK에도 영향을 받습니다. 이 저장소에는 target SDK를 판단할 앱 Gradle이 없습니다. [Android 권한 문서](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

Android 8 이상에서 소리·진동·방해 수준은 채널 설정과 사용자 선택에 좌우됩니다. 채널을 만든 뒤 같은 ID로 재생성해도 importance를 앱 마음대로 바꿀 수 없습니다. 서버 sound=default만으로 실기기 소리가 난다고 판단할 수 없습니다. [Android 채널 문서](https://developer.android.com/develop/ui/compose/notifications/channels)

서버 측 channel 지정은 [buildAndroidConfig()][S46]에 있으며, 실제 채널 생성 코드는 모바일 저장소에서 검증해야 합니다.

## 11.2 제조사·네트워크·절전

삼성 Galaxy의 절전/앱 절전/알림 카테고리, 샤오미의 백그라운드 제한, 백그라운드 데이터 제한은 실제 판매 지역·OS·설정에 따라 시험해야 합니다. FCM 의존 기기는 Google Play services 가용성도 전제입니다.

- **코드로 개선:** 채팅 priority, 실제 foreground 판정, reconnect 후 REST 복구, token/권한 재등록, 채널 설정 안내, 발송 만료·재시도.
- **OS/사용자 선택:** 강제 중지, 알림/채널 차단, DND·Focus, 잠금화면 미리보기, 제조사 배터리 제한. 앱이 강제로 우회하거나 항상 수신한다고 약속할 수 없음.
- **재부팅:** 첫 잠금 해제 전 수신은 별도 direct-boot 설정 검증 항목. 현재 서버 메시지/저장소만으로 지원한다고 볼 수 없음.
- **미사용·오프라인:** 서버 DB 영속성과 FCM 보관은 서로 다릅니다. TTL 미지정 메시지는 기본 보관 정책의 영향을 받으며, 만료·합침·장기 비활성 상태까지 고려해야 합니다. [FCM 수명 정책](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan)

## 11.3 iOS

**iOS는 “현재 대상 아님”으로 제외할 수 없습니다.** 서버가 IOS 플랫폼과 APNs 설정을 명시하고 있습니다. 정확한 상태는 **“서버 지원 대상, iOS 앱 구현·배포 설정 미제공으로 검증 불가”**입니다.

| 점검 항목 | 확인/미확인 |
| --- | --- |
| FCM→APNs 구조 | 있음. 직접 APNs sender 없음 |
| Firebase APNs Key/Certificate | Console에 등록됐는지 미확인. 서버 service-account 파일과 다른 설정 |
| Push Notification capability | Xcode 프로젝트/서명 entitlements 없어 미확인 |
| aps-environment / production | 배포 provisioning/서명 산출물 확인 필요 |
| Background Modes / remote notifications | 미확인. silent push 처리에 필요한 설정과 사용자 표시 alert 수신을 구분해야 함 |
| 권한 요청/거부 UX | 미확인. authorizationStatus, provisional 등 상태가 서버 Boolean에 어떻게 반영되는지 확인 |
| APNs token↔FCM token | 앱 SDK의 자동 연결 또는 수동 설정 확인 필요. 서버 apnsToken 필드만으로 증명 불가 |
| foreground 표시 | UNUserNotificationCenterDelegate의 willPresent 및 표시 옵션 미확인 |
| background/terminated 표시 | 서버 alert payload는 있음. 실제 production APNs 경로 검증 필요 |
| 강제 종료 | 백그라운드 앱 코드 실행을 보장할 수 없음. alert의 OS 표시·클릭을 별도 시험 |
| 클릭/deep link | notification response handler, 로그인 지연, router readiness, URL scheme 설정 미확인 |
| badge | 서버 aps.badge 없음. 알림함 unreadCount와 아이콘 숫자 동기화 구현 미확인 |

APNs 인증 키 업로드와 클라이언트 token 연결은 [Firebase Apple 설정 문서](https://firebase.google.com/docs/cloud-messaging/ios/get-started), foreground/클릭/silent 처리의 구분은 [Apple 플랫폼 FCM 수신 문서](https://firebase.google.com/docs/cloud-messaging/ios/receive-messages)를 기준으로 확인해야 합니다. 현재 payload는 content-available을 보내지 않으므로 silent background 처리가 없다는 이유만으로 표시용 푸시도 반드시 실패한다고 판정하지 않았습니다.

# 12. 실제 스마트폰 테스트 시나리오

아래는 **수행할 검증 계획과 코드상 예상**입니다. 실제 스마트폰으로 실행한 결과가 아닙니다.

- PASS: 명시한 서버 동작이 코드상 존재함. 실제 기기 최종 합격은 아님.
- 위험: 모바일/OS/경합·운영 설정 확인이 필요하거나 조건에 따라 지연·누락 가능.
- FAIL: 조건을 만들면 현재 코드가 목표 동작을 충족하지 못하는 경로가 확인됨.

## 12.1 필수 18개와 추가 회귀 시나리오

| 번호 | 조건 → 행동 | 기대 결과 | 실제 코드상 예상 결과 | 판정 |
| --- | --- | --- | --- | --- |
| 1 | 앱 실행 중 → 다른 사용자가 매칭 요청/채팅 전송 | 즉시 인앱 갱신, 의도한 배지 | 채팅 STOMP 존재. 전체 알림함 자동 갱신/FCM foreground 미확인 | 위험 |
| 2 | 앱 background → 새 매칭 요청·채팅 | OS 표시 | 일반 혼합 payload 가능. 채팅 구독 잔존 시 생성 생략 | 위험 |
| 3 | 일반 process 종료 → 이벤트 | OS 표시, 클릭하면 실행 | OS 경로 가능. 앱 설정/cold start 미확인 | 위험 |
| 4 | 매칭/채팅/그룹 알림 클릭 | 올바른 화면 | deeplink/key 제공, 모바일 route 미확인 | 위험 |
| 5 | 권한 거부 및 서버 permission=false → 이벤트 | OS 알림 없음, 허용된 인앱 기록 유지 | 일반 경로 outbox 생략, 인앱 정책에 따라 DB 노출 | PASS: 서버 정책 한정 |
| 6 | 권한 거부 후 시스템에서 허용 → 앱 복귀 → 이후 이벤트 | 이후 푸시 재개 | PATCH/재등록이 필요. 앱이 누락하면 계속 미발송 | 위험 |
| 7 | A가 logout API만 호출 → A 알림 발생 | 수신 중단 | push_devices 유지 → 계속 발송 가능 | FAIL / N01 |
| 8 | A 알림 큐 적재 → A logout/기기 해제 → B 같은 token 등록 → 큐 처리 | A 알림 노출 없음 | B가 사용하는 기기로 A snapshot 발송 | FAIL / N02 |
| 9 | FCM token refresh → 신규 token POST → 이후 이벤트 | 신규 token 사용 | 일반 신규 outbox는 새 token 사용. 기존 큐는 옛 token | 위험 |
| 10 | 삭제→재설치→로그인→기기 등록 | 새 설치에 수신, 옛 등록 정리 | 등록 API는 있음. 재설치 callback/옛 행 정리 미검증 | 위험 |
| 11 | 스마트폰 재부팅 → 잠금 해제 전/후 각각 이벤트 | 지원 범위대로 수신, 누락 상태 복구 | direct boot 설정 미확인, 해제 후 경로도 기기 검증 필요 | 위험 |
| 12 | 화면 꺼짐·Doze/절전 → 채팅 | 합의한 지연 한도 내 표시 | NORMAL 지정으로 지연 가능 | 위험 / N08 |
| 13 | 인터넷 끊김 → 여러 방 이벤트 → 재연결 | 알림 또는 서버 상태 복구 | FCM collapse/만료, STOMP replay 없음, REST 복구 필요 | 위험 |
| 14 | 같은 계정 두 스마트폰 → 이벤트 | 정의한 다중 기기 정책대로 수신 | 일반 알림은 기기별 큐. 한 기기가 방 구독 시 채팅은 사용자 전체 생략 | 위험 |
| 15 | Android 같은 방에 1초 간격으로 60초 전송 | 과도한 알림 없이 최대 지연 내 첫 알림 | coalesce가 매번 now+2초로 연기 | FAIL: 최대 지연 요구 시 / N09 |
| 16 | 서로 다른 푸시를 연속 클릭 | 중복 화면·엉뚱한 방·crash 없음 | 앱 navigation 코드 미확인 | 위험 |
| 17 | 삭제/종료/나간 방의 과거 알림 클릭 | 접근 불가 안내, 안전한 복귀 | 서버 방/참여 검증 있음, 앱 오류 처리 미확인 | 위험 |
| 18 | outbox 적재 → 사용자 탈퇴 → 워커 발송 | 탈퇴 후 발송 없음 | token 소유권은 해제하지만 과거 outbox로 발송 가능 | FAIL / N02 |
| 19 | 그룹 매칭 완료, 외부 Redis 소비자 없음 | OS 성사 알림 | 일반 outbox 없음, Redis publish로 종료 | FAIL / N03 |
| 20 | 방 구독 → 앱 background, 구독 유지 → 새 채팅 | 미읽음 유지와 OS 알림 | 이미 열람으로 판정해 자동 읽음+푸시 생략 | FAIL / N04 |
| 21 | FCM 성공 응답 직후 worker 종료, SENT 저장 전 | 중복을 탐지/흡수하며 복구 | 5분 timeout 이후 재발송 가능 | 위험 / N11 |
| 22 | 일시 FCM 오류 3회 → 복구 | 정의한 재시도/운영 알림 | 약 1분/5분 재시도 후 FAILED. GitHub 관리자 수동 재전송 가능 | PASS: 코드 경로 한정 |
| 23 | 잘못된 payload로 INVALID_ARGUMENT 응답 | payload 실패만 기록, 정상 token 유지 | token release, 이후 신규 푸시 중단 | FAIL / N05 |
| 24 | FCM=false, worker=true → 이벤트 | 운영 구성 오류 탐지 | simulated-ID로 SENT | FAIL / N06 |
| 25 | B의 JWT로 A notificationId 읽음·삭제 | 거절, A 상태 불변 | id+userId 조건으로 NOT_FOUND | PASS: 코드상 owner 검증 |
| 26 | B가 A notificationId로 OPENED 반복 전송 | owner 검증/중복 제한 | 이벤트 저장 가능, 원본 읽음은 바뀌지 않음 | FAIL: 추적 무결성 / N14 |
| 27 | Android 설정 강제 중지 → 이벤트 → 수동 재실행 | OS 제약 안내 및 재실행 후 상태 복구 | 강제 중지 동안 수신 보장 불가. 앱 재조회 미확인 | 위험: OS 제한 |
| 28 | 같은 계정 기기 1에서 읽음 → 기기 2 목록 유지/재조회 | 재조회 시 읽음 일치, 실시간 정책 확인 | DB 공유, 즉시 갱신 push 없음 | PASS: 재조회 한정 |
| 29 | 앱/worker 서버만 재시작, Redis 유지 → 채팅 | 죽은 구독 정리, push 정상 | 24시간 session 값이 남으면 열람 오판 가능 | 위험 / N04 |
| 30 | 최초 기기 등록/기본 설정 없는 동일 사용자에 동시 요청 | 일관된 한 행, 전체 요청 정상 처리 | 조회→저장 경합 및 unique 충돌 가능 | 위험 / N16 |
| 31 | 기존 무음 채널 설치 상태 → 앱 업데이트 → 푸시 | 기존 설정 존중, 정확한 안내 | 서버 sound/priority 변경만으로 채널 소리 복구 불가 | 위험 |
| 32 | 긴 FCM 지연 중 worker 2대, batch 처리 5분 초과 | 같은 작업 중복 소유 방지 | timeout 복구와 늦은 기존 worker 결과가 경합 가능 | 위험 / N11 |

8번은 “전환 후 새 A 알림”과 “전환 전 만들어진 A 대기 알림”을 각각 시험해야 합니다. 같은 T에 대해 B 등록이 성공했다면 새 A 알림은 A의 released 행을 사용하지 않지만, 과거 대기 알림 문제는 남습니다.

## 12.2 최소 기기·OS 매트릭스

| 기기군 | 최소 선택 | 핵심 확인 |
| --- | --- | --- |
| Android 기준 기기 | 테스트 시점 최신 안정 Android의 Pixel급 GMS 기기 | 표준 FCM·권한·Doze·프로세스 종료 |
| Android 13 | API 33 실기기 또는 해당 OS 기기 | POST_NOTIFICATIONS 최초 설치/거부/허용 전환 |
| Samsung Galaxy | 실제 출시 대상 One UI 기기 | 앱 절전·카테고리·배경 데이터·최근 앱 제거 |
| Android 8–12 | 앱이 지원한다면 최소 지원 범위 기기 추가 | 채널 생성·기존 설치 업데이트 |
| Xiaomi | 해당 기기/지역을 지원한다면 추가 | GMS 여부·자동 시작·제조사 배터리 제한 |
| iPhone | 테스트 시점 최신 안정 iOS 기기 | production APNs/TestFlight, 표시·클릭·Focus·미리보기 |
| 구형 iPhone/iOS | 앱 최소 지원 iOS가 다르면 추가 | 최소 지원 버전 Lifecycle·권한 호환성 |

실제 target/min SDK와 iOS deployment target이 없어 버전 번호를 임의 확정하지 않았습니다.

각 기본 기기에서 다음을 조합합니다.

| 상태 × 권한 | 확인 내용 |
| --- | --- |
| Foreground × 허용/거부 | 인앱 갱신, OS 표시 정책, 중복, 권한 거부가 인앱 조회를 막지 않는지 |
| Background × 허용/거부 | 잠금화면/알림 센터, 소리/진동, callback와 수신 이벤트 차이 |
| 일반 Terminated × 허용/거부 | OS 표시와 cold start 클릭, data 보존·한 번만 이동 |
| 사용자 강제 종료 × 허용/거부 | Android 강제 중지와 iOS swipe 종료를 분리, 재실행 후 복구 |
| 추가: Doze/절전, 네트워크 끊김, Wi-Fi↔셀룰러 | 지연·누락·오래된 구독, REST 동기화 |
| 추가: 재부팅 전후, 신규 설치/업데이트 설치 | 토큰·권한·기존 채널·첫 잠금 해제 |

최소 핵심 조합은 기기당 4개 상태 × 권한 2개 = 8개이며, 각 조합에서 매칭 요청/채팅/그룹 성사를 구분합니다. 백그라운드 직후뿐 아니라 1분·15분·장시간 대기 후를 시험합니다. 그룹 성사 FAIL은 다른 일반 매칭 알림의 성공으로 대체 판정하면 안 됩니다.

## 12.3 관측·합격 기준

테스트 계정 두 개와 다중 기기 계정을 준비하고 release 서명 Android 앱 및 TestFlight/배포용 iOS 앱을 사용합니다. Firebase Console 단독 테스트 메시지만으로 끝내지 않고 **실제 매칭/채팅 이벤트→DB→outbox→FCM→OS→클릭**을 추적합니다.

기록할 항목: 앱 버전/OS/기종/권한/채널/절전 상태, 도메인 event ID, notificationId, outboxId, 기기 식별자의 마스킹/해시, 생성·claim·FCM 접수·OS 표시·클릭 시각, 실패 코드, 실제 목적 화면. 토큰 원문과 실제 사용자 메시지를 테스트 로그에 남기지 않습니다.

- 타 계정 알림 노출 0건, logout/탈퇴 후 새 발송 차단.
- 일반 온라인 상태의 중요 이벤트는 합의한 표시 지연 목표를 만족. 예시 목표를 정한다면 10초 이내로 설정하고 실제 p95/p99를 기록하되, 이는 현재 측정값이 아님.
- 강제 중지·OS 차단은 일반 수신율과 분리하고, 재실행 후 서버 상태 복구를 확인.
- 중복 재시도는 같은 notificationId로 추적하고 UI가 중복을 흡수하는지 확인.
- 클릭 시 계정·권한 일치, 삭제 데이터 안내, crash/빈 화면 0건.
- 백그라운드 혼합 메시지는 앱 수신 callback이 없을 수 있으므로 RECEIVED 미기록을 곧바로 OS 미표시로 계산하지 않음.
- notification inbox에서 제외된 채팅은 채팅 목록/메시지 REST를 별도 확인.

# 13. 배포 전 체크리스트

**서버 코드·데이터**

- [ ] 로그아웃과 push device 해제·계정 전환 프로토콜을 하나의 검증 가능한 계약으로 정리.
- [ ] PENDING/PROCESSING/FAILED→재시도 모두에 수신자·기기·token 세대/상태 검증 및 취소 경합 처리.
- [ ] 이미 FCM에 접수된 notification은 서버에서 회수할 수 있다는 가정을 제거. 민감 본문 최소화 및 앱 내 기존 알림 정리 검토.
- [ ] GROUP_MATCHED의 실제 FCM 발송 경로 완결.
- [ ] 채팅 방 구독과 실제 foreground 열람·읽음을 분리, 서버 재시작 시 잔존 상태 검증.
- [ ] INVALID_ARGUMENT payload 오류에서 유효 token이 제거되지 않는 회귀 테스트.
- [ ] MySQL의 unique/index/JSON/SKIP LOCKED가 실제 schema에서 일치하는지 확인.
- [ ] 일반 알림·outbox의 원자성, 동시 dedupe, worker 중단/복구, 다중 worker claim 통합 테스트.
- [ ] push events owner·기기·중복 검증.
- [ ] 오래된 실패·토큰·알림 보존/정리와 민감정보 로그 마스킹 정책 확인.

**운영 설정**

- [ ] 실제 배포 commit/image digest와 분석 commit 차이 확인.
- [ ] 실행 중 SPRING_PROFILES_ACTIVE와 notification.outbox.worker.enabled, notification.push.fcm.enabled를 민감정보 없이 확인.
- [ ] 기본 application.yml은 둘 다 false, dev는 환경변수 기본 true라는 차이를 인지. “항상 꺼짐/항상 켜짐”으로 가정하지 않기.
- [ ] Compose의 값 없는 환경변수 전달·잘못된 프로필·빈 credential 경로 시험.
- [ ] FIREBASE_PROJECT_ID, Android/iOS 앱의 Firebase 프로젝트가 일치.
- [ ] FIREBASE_CREDENTIALS_PATH가 컨테이너 내부 read-only 마운트 파일을 가리키며 실제 계정 권한으로 FCM 호출 가능한지 확인.
- [ ] iOS APNs production 인증 키/인증서, Bundle ID, entitlement/provisioning 확인.
- [ ] simulated 발송기를 운영에서 사용하지 않도록 검사.
- [ ] PENDING 최고 대기시간, FAILED, PROCESSING timeout, FCM 오류·token 해제 급증에 경보.
- [ ] GitHub Actions의 build/test 성공을 실기기 푸시 검증으로 대체하지 않기. 현재 workflow는 build/test 중심. [CI][S40]

**모바일**

- [ ] AndroidManifest·iOS 프로젝트·Firebase 설정 파일 제공 및 실제 release build 검사.
- [ ] 알림 권한 요청, 거부 후 설정 이동, resume 시 서버 권한 갱신.
- [ ] 채팅 channel ID 일치, 기본 채널, 소리/진동/importance, 기존 설치 유지 정책.
- [ ] token 최초 등록/refresh/재설치/계정 변경 실패 재시도.
- [ ] foreground 표시·background OS 중복 방지·cold start 클릭·초기화 준비 대기.
- [ ] 알림함/배지/채팅 재접속 후 REST 복구, 계정별 캐시 초기화.
- [ ] 12절 핵심 실기기 매트릭스와 P0 회귀 시나리오 통과.

# 14. 수정 우선순위

## Phase 1 — 배포 전 반드시 수정·확인

1. **N01/N02 개인정보 경계:** 로그아웃·기기 해제·계정 변경·탈퇴와 큐 발송을 연계합니다. 발송 전 재검증과 큐 무효화를 함께 적용하고 이미 provider로 넘어간 알림의 잔여 위험은 본문 최소화로 줄입니다.
2. **N03 그룹 성사 알림:** 기존 DB outbox를 사용하도록 경로를 완결하거나 실제 외부 소비자 전체 경로를 확보·검증합니다.
3. **N04 채팅 열람 판정:** background에 남은 구독 때문에 자동 읽음/푸시 생략이 발생하지 않도록 합니다.
4. **N05 오류 분류 / N06 설정:** payload 오류의 정상 token 폐기 방지, 운영 simulated 성공 차단을 우선 처리합니다.
5. **N07 모바일 배포 검증:** Android/iOS 권한·채널·FCM/APNs·foreground·background·cold start 클릭을 실제 배포 빌드로 검증합니다.
6. 12절 7/8/18/19/20/23/24번을 출시 차단 회귀 시나리오로 사용합니다. 수정 후 실제 결과가 있어야 판정을 다시 READY_WITH_FIXES 또는 READY로 바꿀 수 있습니다.

## Phase 2 — 배포 직후 안정화

- Android 채팅 priority와 2초 합침의 최대 지연 정책.
- outbox expiresAt, 전송 시 quiet hours/설정 적용, 만료 콘텐츠 클릭 처리.
- 알림함·읽음·다중 기기 배지의 재조회/실시간 정책.
- 수신·표시·열람 지표 분리와 push event 검증.
- stale token 관리, 기기 해제 UX, 운영 실패 재전송 절차.
- DB 커밋 후 실시간 이벤트 발행 및 클라이언트 중복 처리.

## Phase 3 — 사용자 증가 시 개선

- 실제 이벤트율·p95/p99·backlog 측정 후 전용 scheduler와 제한된 발송 동시성 도입.
- 5분 claim timeout과 실제 batch 최장 처리시간을 맞추고 claim 소유권을 검증.
- JSON 기반 pending 채팅 조회, 알림함·미읽음·토큰 조회의 EXPLAIN 및 인덱스 조정.
- 필요하면 Firebase SDK의 제한된 병렬 전송 기능을 검토하되 현재 DB outbox의 원자성/개인정보 검증을 유지.
- 여러 서버를 운영하면 로컬 simple broker로 직접 발행하는 채팅목록·그룹 상태 이벤트의 인스턴스 간 전달을 검증. 현재 채팅 메시지 Redis 경로와 직접 STOMP 경로는 동일하지 않음. [WebSocketConfig][S23], [그룹 이벤트][S15]


[S1]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationService.java#L47
[S2]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationOutboxWorker.java#L20
[S3]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/FirebasePushNotificationSender.java#L75
[S4]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/PushDeviceService.java#L32
[S5]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/domain/entity/PushDevice.java#L27
[S6]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/auth/service/AuthService.java#L175
[S7]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/user/service/UserService.java#L324
[S8]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationInboxService.java#L29
[S9]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationPreferenceService.java#L73
[S10]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/ChatService.java#L1169
[S11]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/ChatService.java#L416
[S12]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/ChatService.java#L678
[S13]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/groupmatching/service/GMatchingService.java#L1142
[S14]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/groupmatching/service/GRedisMatchingPushService.java#L28
[S15]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/groupmatching/service/GMatchingEventPublisher.java#L18
[S16]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/repository/NotificationOutboxRepository.java#L99
[S17]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/domain/entity/NotificationOutbox.java#L198
[S18]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationOutboxService.java#L30
[S19]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/PushEventService.java#L24
[S20]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/controller/PushDeviceController.java#L53
[S21]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/controller/NotificationController.java#L48
[S22]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/matching/service/MatchingService.java#L571
[S23]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/global/config/WebSocketConfig.java#L66
[S24]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/RedisSubscriber.java#L23
[S25]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/config/FirebasePushConfig.java#L32
[S26]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/LoggingPushNotificationSender.java#L13
[S27]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/resources/application.yml#L70
[S28]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/resources/application-dev.yml#L189
[S29]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/docker-compose.yml#L68
[S30]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/global/config/SecurityConfig.java#L59
[S31]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/global/security/resolver/CurrentUserIdArgumentResolver.java#L39
[S32]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/admin/AdminOperationsService.java#L482
[S33]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/auth/domain/entity/RefreshToken.java#L18
[S34]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/domain/NotificationType.java#L7
[S35]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/domain/entity/Notification.java#L33
[S36]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/global/security/stomp/StompHandler.java#L220
[S37]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/dto/response/PushDeviceResponse.java#L39
[S38]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/build.gradle#L17
[S39]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/global/config/RedisConfig.java#L29
[S40]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/.github/workflows/ci.yml#L14
[S41]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/.gitignore#L115
[S42]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/admin/AdminUserPurgeService.java#L60
[S43]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/ChatService.java#L1303
[S44]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/groupmatching/service/GMatchingService.java#L879
[S45]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/FirebasePushNotificationSender.java#L232
[S46]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/FirebasePushNotificationSender.java#L119
[S47]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/notification/service/NotificationService.java#L221
[S48]: https://github.com/jaemin-devlog/AirConnect/blob/2f7c360d0077b562f678fc74708630ad5e97b08e/src/main/java/univ/airconnect/chat/service/ChatService.java#L304
