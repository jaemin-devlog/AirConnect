# AirConnect 알림 안정화 Phase 3 검증 보고서

검증일: 2026-09-09 (Asia/Seoul)

## A. 최종 판정

```text
DEVICE_VALIDATION_BLOCKED
```

현재 작업 공간과 AirConnect Git 저장소 전체에서 Android/iOS 모바일 애플리케이션 소스를 찾지 못했다. `AndroidManifest.xml`, Android app module, `google-services.json`, FCM 수신 서비스, `.xcodeproj`, `.xcworkspace`, `Info.plist`, entitlements, `GoogleService-Info.plist`가 모두 없다. Git의 모든 로컬/원격 추적 ref와 전체 object 목록에서도 모바일 프로젝트 이력을 확인하지 못했다.

또한 현재 Windows 환경에는 Android SDK, ADB, Emulator, Flutter, React Native CLI, Xcode가 없고 실제 Firebase 프로젝트 ID 및 credential source도 설정되어 있지 않다. 따라서 FCM provider 접수부터 스마트폰 표시·클릭까지의 실제 경로를 실행할 수 없다.

Phase 1·2 서버 회귀와 FCM 메시지 계약은 자동 검증에 통과했다. 그러나 이 결과는 기기 수신 성공을 뜻하지 않는다. 현재 상태는 계속 `READY_FOR_DEVICE_VALIDATION`이며, `READY`로 올릴 증거는 없다.

## B. 테스트 환경

| 항목 | 실제 환경/결과 |
|---|---|
| Android 기종 | `NOT_EXECUTED` — 연결 기기 및 ADB 없음 |
| Android 버전 | `NOT_EXECUTED` |
| Android 앱 build | `BLOCKED_MOBILE_SOURCE_NOT_AVAILABLE` |
| 서버 build | `0.0.1-SNAPSHOT`, Java 17, Spring Boot 3.4.3 |
| 서버 commit | `749ac531e5d2c9e5f85f3077b17fd2b6e863c2b9` + Phase 1·2 미커밋 working tree |
| 네트워크 | 기기 Wi-Fi/LTE/5G 테스트 `NOT_EXECUTED` |
| Backend Firebase | Firebase Admin SDK 9.7.0 코드 존재. 현재 환경의 `FIREBASE_PROJECT_ID`, credential path, ADC는 설정되지 않음 |
| Android Firebase | `BLOCKED_MOBILE_SOURCE_NOT_AVAILABLE` |
| iOS Firebase | `BLOCKED_MOBILE_SOURCE_NOT_AVAILABLE` |
| iOS 실행 환경 | `IOS_DEVICE_VALIDATION_BLOCKED` — Windows이며 macOS/Xcode/TestFlight/iPhone 환경 없음 |

Firebase 프로젝트 일치 여부는 세 앱 중 모바일 두 앱의 설정이 없고 backend runtime project도 확정할 수 없어 `NOT_EXECUTED`다. credential/private key/token 원문은 읽거나 기록하지 않았다.

## C. Android 결과

| Scenario | Result | Evidence |
|---|---|---|
| Foreground | `NOT_EXECUTED` | Android 앱 소스·build·기기 없음 |
| Background | `NOT_EXECUTED` | FCM 실제 발송 및 OS 표시 미실행 |
| Terminated | `NOT_EXECUTED` | 프로세스 종료 및 cold start 클릭 미실행 |
| Recent Apps removed | `NOT_EXECUTED` | Android 실행 환경 없음 |
| Force Stop | `NOT_EXECUTED` | Android 실행 환경 없음. 실행 시 재실행 전 수신 불가는 `EXPECTED_OS_LIMITATION`으로 분류해야 함 |
| Permission denied | `NOT_EXECUTED` | Manifest/runtime permission 구현을 확인할 모바일 소스 없음 |
| Permission re-enabled | `NOT_EXECUTED` | resume 시 서버 permission 재동기화 구현 미확인 |
| Network reconnect | `NOT_EXECUTED` | Wi-Fi/LTE 기기 테스트 미실행 |
| Doze | `NOT_EXECUTED` | ADB/기기 없음 |

## D. 알림 종류별 결과

| 종류 | 서버 자동 검증 | 실제 기기 결과 |
|---|---|---|
| MATCH | 일반 notification/outbox payload와 HIGH Android delivery priority 구성 확인 | `NOT_EXECUTED` |
| CHAT | `CHAT_MESSAGE_RECEIVED`, `chatRoomId`, `messageId`, `notificationId`, 채팅 channel/tag/collapse key와 NORMAL delivery priority 구성 확인 | `NOT_EXECUTED` |
| GROUP_MATCHED | `finalGroupRoomId`와 `finalChatRoomId`를 모두 payload에 넣고 일반 outbox로 발송하는 통합 테스트 통과 | `NOT_EXECUTED` |

서버의 `FirebaseMessaging.send()`는 mock으로 검증했다. 실제 FCM accepted/providerMessageId, device displayed, notification clicked 증거는 없다.

## E. Foreground Chat 중복

```text
NOT_EXECUTED
```

서버는 Phase 2 정책에 따라 foreground 여부를 추측하지 않고 `CHAT_MESSAGE_RECEIVED` Push를 생성한다. 따라서 모바일 앱은 같은 메시지를 STOMP와 FCM으로 동시에 받을 수 있다. 모바일 소스가 없어 `messageId`/`notificationId` 기반 화면 중복 제거, 현재 방에서의 foreground OS notification 억제, 인앱 상태 갱신을 확인하지 못했다. 서버 Push suppression을 복원하는 변경은 하지 않았다.

## F. Deep Link

| 대상 | 서버가 생성하는 값 | 클릭 결과 |
|---|---|---|
| Chat | `airconnect://chat/rooms/{chatRoomId}` | `NOT_EXECUTED` |
| Matching request | `airconnect://matching/requests` | `NOT_EXECUTED` |
| Matching chat/team room | `airconnect://chat/rooms/{chatRoomId}`, `airconnect://matching/team-rooms/{teamRoomId}` | `NOT_EXECUTED` |
| Group matching | `airconnect://group-chat/final/{finalGroupRoomId}` | `NOT_EXECUTED` |
| Cold start | 서버 data payload에 `deeplink` 포함 확인 | `NOT_EXECUTED` |
| Invalid/deleted resource | 모바일 fallback/권한 재확인 구현 미확인 | `NOT_EXECUTED` |

GROUP_MATCHED 링크에는 `finalGroupRoomId`를 사용하고 별도 `finalChatRoomId`도 payload에 보존한다. 두 ID를 앱이 올바르게 해석하는지는 실행할 수 없었다.

## G. 계정 안전성

| 시나리오 | 서버 회귀 | 실제 기기 |
|---|---|---|
| A 로그인 → logout → A 새 이벤트 | logout 시 해당 `userId + deviceId` 비활성화 테스트 통과 | `NOT_EXECUTED` |
| A outbox → logout → B 동일 기기 → worker | dispatch 직전 active/owner/provider/token 재검증 및 stale outbox SKIPPED 통합 테스트 통과 | `NOT_EXECUTED` |
| A 회원 탈퇴 → A 이벤트/기존 outbox | 비활성 사용자 발송 차단 테스트 통과 | `NOT_EXECUTED` |
| Token T → refresh T2 → 기존 T outbox | 현재 token 불일치 시 SKIPPED 테스트 통과 | `NOT_EXECUTED` |

실제 스마트폰에서 A 알림 0건을 관찰한 증거가 없으므로 계정 안전성의 기기 PASS를 선언하지 않는다. 이미 Firebase에 접수된 뒤 logout한 알림의 지연 도착 경계도 측정하지 못했다.

## H. Notification Channel / Permission

서버는 `CHAT_MESSAGE_RECEIVED`에 Android channel ID `airconnect_chat_push`, sound `default`, notification priority `DEFAULT`, FCM delivery priority `NORMAL`을 설정한다. 비채팅 매칭 알림은 FCM delivery priority `HIGH`를 사용한다.

Android 앱의 다음 항목은 모두 `BLOCKED_MOBILE_SOURCE_NOT_AVAILABLE`이다.

- `POST_NOTIFICATIONS` Manifest 선언
- Android 13+ runtime permission 요청
- 거부/재허용 및 resume 재확인
- `notificationPermissionGranted` 서버 동기화
- `airconnect_chat_push` channel 생성, importance, sound, vibration, description
- default channel과 기존 설치자의 channel 설정 유지

서버에는 기기 등록, permission PATCH, 기기 비활성화 API가 존재하지만 앱이 실제 lifecycle에 연결하는지는 확인하지 못했다.

## I. Doze / Priority

```text
NORMAL chat Push delay measurement: NOT_EXECUTED
```

채팅 FCM delivery priority가 NORMAL인 것은 코드와 자동 테스트로 확인했다. Doze, 화면 OFF, 배터리 절전, Samsung 앱 절전에서 지연을 측정하지 못했으므로 HIGH 변경은 하지 않았다.

## J. iOS

```text
BLOCKED
Reason: iOS mobile source and macOS/Xcode/TestFlight/iPhone environment unavailable
```

Push Notification capability, `aps-environment`, APNs key/certificate, Firebase/APNs token 연결, authorization, `willPresent`, notification response, background/terminated/cold-start navigation을 검증하지 못했다. 서버는 FCM token을 대상으로 APNs 설정을 포함한 메시지를 구성하지만 이것만으로 iOS 배포 경로가 완성됐다고 볼 수 없다.

## K. 발견한 문제

이번 Phase에서 실제 모바일 동작 결함을 재현하지는 못했다. 다음은 제품 결함 확정이 아니라 검증을 막은 조건과 미검증 위험이다.

### K-1. 모바일 소스 부재

```text
Severity: RELEASE_BLOCKER
Reproduction: 저장소/모든 Git ref에서 AndroidManifest.xml, app module, iOS project 및 Firebase mobile config 검색
Root cause: 현재 작업 공간에 실제 모바일 프로젝트가 제공되지 않음
Fix: 없음 — 추측 구현 금지 원칙에 따라 서버 또는 가짜 앱을 추가하지 않음
Retest: 실제 배포 대상 모바일 저장소를 같은 작업 환경에 제공한 뒤 정적 검증부터 재개
```

### K-2. 실제 Firebase 발송 환경 부재

```text
Severity: VALIDATION_BLOCKER
Reproduction: 현재 환경의 Firebase project/credential source와 Compose mount 파일 존재 여부 확인
Root cause: FIREBASE_PROJECT_ID, FIREBASE_CREDENTIALS_PATH, ADC 및 mounted credential 파일이 현재 환경에 없음
Fix: 없음 — credential을 생성하거나 추측하지 않음
Retest: 실제 검증용 Firebase 프로젝트와 권한이 준비된 환경에서 canary token으로 발송
```

### K-3. Android 실행 환경 부재

```text
Severity: VALIDATION_BLOCKER
Reproduction: adb, emulator, Android SDK, Flutter/React Native 도구 확인
Root cause: 실행 도구와 연결 기기가 없음
Fix: 없음
Retest: Android 13+ 기기와 Samsung Galaxy를 ADB로 연결하고 release-equivalent build로 시나리오 실행
```

### K-4. Foreground 중복·permission·channel·cold-start 처리 미검증

```text
Severity: RELEASE_RISK
Reproduction: 모바일 소스 및 실행 build 부재로 코드/기기 확인 불가
Root cause: 검증 대상 앱 artifact 부재
Fix: 없음 — 문제 재현 전 기능 변경 금지
Retest: STOMP + FCM 동시 수신, 권한 거부/재허용, invalid link, terminated click matrix 실행
```

## L. 전체 자동 테스트

실행 명령:

```text
gradle --no-daemon test --rerun-tasks
```

결과:

```text
BUILD SUCCESSFUL in 2m
Suites: 92
Tests: 640
Failures: 0
Errors: 0
Skipped: 21
```

테스트 결과 파일은 2026-09-09 22:47 +09:00에 새로 생성됐다. Phase 1·2의 logout/account switch/deletion/token refresh/permission/owner mismatch/INVALID_ARGUMENT/GROUP_MATCHED/STOMP/multiple-device/chat-coalescing 관련 테스트 클래스가 유지되고 통과했다.

Phase 3에서는 제품 소스 코드를 수정하지 않았다.

## M. 최종 배포 판단

서버의 Phase 1·2 안전성은 캐시 없이 재실행한 640개 테스트로 유지됐다. 그러나 Android/iOS 앱 소스, 실제 Firebase runtime, Android 기기 및 iOS 실행 환경이 없어 foreground/background/terminated 수신, OS 표시, 클릭, cold-start navigation, 계정 전환 오발송 0건, permission/channel, Doze 지연을 실제로 증명하지 못했다. 따라서 실제 사용자 배포를 `READY`로 판정할 수 없다. 모바일 저장소와 release-equivalent build, 검증용 Firebase 환경, Android 13+ 및 Samsung 기기, 그리고 iOS가 출시 대상이면 macOS/Xcode/TestFlight/iPhone을 제공한 뒤 Phase 3를 이어서 실행해야 한다.
