# AirConnect iOS Firebase / APNs 연동 체크리스트

버전: v1.0  
작성일: 2026-03-27

## 1. 목적

이 문서는 iOS 앱이 AirConnect 백엔드와 푸시 알림을 정상적으로 연동하기 위해 반드시 수행해야 하는 Firebase / APNs 설정과 앱 구현 체크리스트를 정리한 문서이다.

현재 AirConnect의 iOS 푸시 전달 경로는 아래와 같다.

`Backend -> FCM -> APNs -> iOS Device`

중요 전제는 다음과 같다.

- 백엔드는 `FCM registration token`을 기준으로 발송한다.
- iOS 앱은 `FCM registration token`을 서버에 등록해야 한다.
- 서버는 `APNs direct provider` 방식으로 직접 발송하지 않는다.
- 서버가 허용하는 `provider`는 현재 `FCM`만이다.
- 서버 배포 설정 기준 프로필은 `dev`이며, 서버 환경값은 `application-dev.yml` 기준으로 구성되어 있다.

관련 운영 API 문서는 아래 파일을 참고한다.

- `docs/ios-notification-api.md`

## 2. Apple Developer 설정

### 2.1 Bundle ID 확인

1. Apple Developer 계정에서 앱의 `Bundle ID`를 확인한다.
2. 이 값은 아래 세 군데에서 완전히 같아야 한다.
   - Apple Developer의 App ID
   - Firebase Console의 iOS 앱 등록 정보
   - Xcode 프로젝트의 `Bundle Identifier`
3. 대소문자나 오타가 있으면 푸시가 정상적으로 연결되지 않는다.

### 2.2 Push Notifications Capability 활성화

1. Apple Developer의 `Certificates, Identifiers & Profiles`로 이동한다.
2. 대상 App ID를 선택한다.
3. `Push Notifications`를 활성화한다.

### 2.3 APNs Auth Key 생성

1. Apple Developer에서 `Keys` 메뉴로 이동한다.
2. 새 키를 생성하고 `Apple Push Notifications service (APNs)` 권한을 활성화한다.
3. 생성 후 아래 값을 확보한다.
   - `.p8` 파일
   - `Key ID`
   - `Team ID`
4. 이 키는 Firebase Console에 업로드할 때 필요하다.

### 2.4 프로비저닝 프로파일 확인

Push 관련 capability를 변경했다면 프로비저닝 프로파일이 갱신되었는지 확인한다.

- 개발용 프로파일
- 배포용 프로파일

기존 프로파일이 오래된 상태면 실기기 테스트에서 푸시 등록이 실패할 수 있다.

## 3. Firebase Console 설정

### 3.1 같은 Firebase 프로젝트 사용

반드시 아래 두 주체가 같은 Firebase 프로젝트를 사용해야 한다.

- AirConnect 백엔드
- iOS 앱

프로젝트가 다르면 서버는 발송 성공처럼 보여도 iOS 단말에는 도착하지 않거나 `PERMISSION_DENIED`, `SENDER_ID_MISMATCH` 등의 문제가 발생할 수 있다.

### 3.2 iOS 앱 등록

1. Firebase Console에서 AirConnect 프로젝트로 이동한다.
2. `프로젝트 설정 -> 일반`에서 iOS 앱을 추가한다.
3. `Bundle ID`를 입력한다.
4. `GoogleService-Info.plist`를 다운로드한다.

### 3.3 APNs Auth Key 업로드

1. Firebase Console에서 `프로젝트 설정 -> Cloud Messaging`으로 이동한다.
2. `Apple app configuration` 또는 APNs 관련 섹션으로 이동한다.
3. 아래 값을 업로드한다.
   - `.p8` 파일
   - `Key ID`
   - `Team ID`
4. 이 단계가 빠지면 Firebase는 iOS 기기로 APNs 전달을 할 수 없다.

### 3.4 Web 설정과 혼동하지 않기

웹 푸시용 `VAPID key`는 iOS 앱 푸시와 별개다.

- iOS 앱은 `GoogleService-Info.plist`와 APNs Auth Key 설정이 핵심이다.
- `apiKey`, `authDomain`, `messagingSenderId`, `vapidKey`는 웹 테스트 페이지용 값이다.

## 4. Xcode 설정

### 4.1 GoogleService-Info.plist 추가

1. Firebase Console에서 받은 `GoogleService-Info.plist`를 Xcode 프로젝트에 추가한다.
2. 앱 타깃에 포함되었는지 확인한다.
3. 잘못된 Firebase 프로젝트 파일을 넣지 않았는지 확인한다.

### 4.2 Signing & Capabilities

앱 타깃에서 아래 capability를 추가한다.

- `Push Notifications`

필요 시 아래 capability도 추가한다.

- `Background Modes`
  - `Remote notifications`

현재 백엔드는 일반 표시형 푸시를 기준으로 동작한다.  
하지만 향후 silent push나 background fetch까지 다루려면 `Remote notifications`가 필요하다.

### 4.3 Firebase SDK 설치

최소 아래 SDK가 필요하다.

- `FirebaseCore`
- `FirebaseMessaging`

Swift Package Manager를 쓰는 경우 Firebase iOS SDK에서 위 모듈을 포함해 설치한다.

## 5. 앱 시작 시 기본 구현

### 5.1 필수 초기화 순서

앱 시작 시 아래가 수행되어야 한다.

1. `FirebaseApp.configure()`
2. `UNUserNotificationCenter.current().delegate` 설정
3. `Messaging.messaging().delegate` 설정
4. 알림 권한 요청
5. `application.registerForRemoteNotifications()` 호출

### 5.2 최소 예제

```swift
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()

        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self

        let options: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(options: options) { _, _ in }
        application.registerForRemoteNotifications()

        return true
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        print("FCM token:", fcmToken)
        // 로그인 직후 또는 토큰 갱신 시 서버로 전송
    }
}
```

## 6. APNs Token과 FCM Token 연결

이 부분은 iOS에서 가장 자주 빠지는 설정 중 하나다.

### 6.1 기본 동작

- Firebase Messaging이 정상적으로 구성되면 APNs token과 FCM token이 연결된다.
- 다만 앱 구조나 설정에 따라 명시적으로 APNs token을 Firebase에 연결하는 코드가 필요할 수 있다.

### 6.2 구현 예제

```swift
func application(_ application: UIApplication,
                 didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    Messaging.messaging().apnsToken = deviceToken
}
```

이 코드를 누락하면 `FCM -> APNs` 매핑이 정상적으로 되지 않을 수 있다.

## 7. 서버에 디바이스 토큰 등록

### 7.1 등록 시점

아래 시점마다 서버 등록 또는 갱신이 필요하다.

- 로그인 직후
- `didReceiveRegistrationToken`으로 새 토큰을 받았을 때
- 사용자가 알림 권한을 다시 허용했을 때
- 앱 재설치 후 첫 실행 시

### 7.2 등록 API

- 권장 경로: `POST /v1/push/devices`
- 호환 경로: `POST /api/v1/push/devices`

### 7.3 요청 헤더

```http
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json
```

### 7.4 요청 JSON 예시

```json
{
  "platform": "IOS",
  "deviceId": "ios-iphone15pm-001",
  "fcmToken": "fNQ1x9W7R6u1wAqKJmT4Pq:APA91bH7x0J2m8v3K6x1T9w2Y4s8D1n5U7p9Q3r6S2t4V8y1Z5a7B9c2D4e6F8g0H1i3J5k7L9m1N3p5Q7r9S1t3U5v7W9x1Y3",
  "pushEnabled": true,
  "appVersion": "1.0.3",
  "osVersion": "18.3.1",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul",
  "apnsToken": "6f2bd8ea3dd9b4c0a6af22f1e9c5d6b7a8c9d0e1f2233445566778899aabbccd"
}
```

### 7.5 필드 규칙

- `platform`은 반드시 `IOS`
- `deviceId`는 앱 삭제 전까지 최대한 안정적으로 유지
- `fcmToken`은 서버 발송 대상이 되는 실제 FCM registration token
- `pushEnabled`는 현재 디바이스에서 푸시 허용 여부
- `apnsToken`은 선택값이며 진단 또는 추적 목적으로만 저장

## 8. 서버에서 내려오는 푸시 Payload

현재 서버는 FCM `data` payload에 아래 공통 키를 넣는다.

```json
{
  "notificationId": "250",
  "type": "SYSTEM",
  "notificationType": "MATCH_REQUEST_ACCEPTED",
  "title": "매칭이 수락되었어요",
  "body": "지금 바로 대화를 시작해보세요.",
  "deeplink": "airconnect://chat/rooms/3004",
  "sentAt": "2026-03-27T13:00:00"
}
```

### 8.1 파싱 규칙

- FCM `data`는 문자열 map이므로 숫자처럼 보이는 값도 문자열로 받아야 한다.
- 1차 분기는 `type`
- 세부 분기는 `notificationType`
- 이동 경로는 `deeplink`

### 8.2 현재 payload에 없는 값

현재 서버의 기본 `data` payload에는 `providerMessageId`가 포함되지 않는다.

따라서 앱에서 `POST /v1/push/events`를 호출할 때 `providerMessageId`가 꼭 필요하다면 아래 중 하나를 선택해야 한다.

1. iOS 클라이언트에서 Firebase SDK가 제공하는 메시지 ID를 사용한다.
2. 백엔드가 추후 `providerMessageId`를 payload에 넣도록 확장한다.

연동 전에 이 기준을 백엔드와 앱팀이 합의해야 한다.

## 9. 수신 처리 구현

### 9.1 포그라운드 수신

```swift
func userNotificationCenter(_ center: UNUserNotificationCenter,
                            willPresent notification: UNNotification) async
-> UNNotificationPresentationOptions {
    let userInfo = notification.request.content.userInfo
    print(userInfo)
    return [.banner, .sound, .list]
}
```

### 9.2 사용자가 알림을 탭했을 때

```swift
func userNotificationCenter(_ center: UNUserNotificationCenter,
                            didReceive response: UNNotificationResponse) async {
    let userInfo = response.notification.request.content.userInfo
    print(userInfo)
    // notificationId, notificationType, deeplink 기반 라우팅
}
```

### 9.3 Background / Silent 처리

background 또는 silent push를 쓰려면 아래도 구현할 수 있다.

```swift
func application(_ application: UIApplication,
                 didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                 fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
    print(userInfo)
    completionHandler(.newData)
}
```

## 10. Push Event 수집 API

앱팀 요청안 기준으로 푸시 수신 및 오픈 이벤트는 아래 API로 보낼 수 있다.

- 권장 경로: `POST /v1/push/events`
- 호환 경로: `POST /api/v1/push/events`

### 10.1 요청 JSON 예시

```json
{
  "notificationId": "250",
  "providerMessageId": "projects/airconnect-19762/messages/659d5744-dd71-419f-badc-a44e6356cac7",
  "eventType": "RECEIVED",
  "occurredAt": "2026-03-27T04:00:00",
  "deviceId": "ios-iphone15pm-001"
}
```

### 10.2 eventType 값

- `RECEIVED`
- `OPENED`

## 11. 실기기 테스트 체크리스트

실단말 테스트는 아래 순서로 확인한다.

1. 앱 설치
2. 알림 권한 허용
3. `FCM registration token` 발급 확인
4. `POST /v1/push/devices` 등록 성공 확인
5. 백엔드 outbox 상태가 `SENT`인지 확인
6. 포그라운드 수신 확인
7. 백그라운드 수신 확인
8. 종료 상태에서 배너 표시 확인
9. 푸시 탭 시 `deeplink` 라우팅 확인
10. `PATCH /api/v1/notifications/{notificationId}/read` 처리 확인
11. `POST /v1/push/events` 수집 확인

## 12. 자주 실패하는 포인트

아래 항목은 실제 연동에서 매우 자주 문제가 된다.

- 백엔드와 iOS 앱이 서로 다른 Firebase 프로젝트를 사용함
- Firebase Console에 APNs Auth Key를 업로드하지 않음
- `Bundle ID`가 Apple Developer / Firebase / Xcode에서 불일치함
- `GoogleService-Info.plist`가 다른 프로젝트용 파일임
- `registerForRemoteNotifications()`를 호출하지 않음
- `didReceiveRegistrationToken`에서 서버에 토큰 갱신을 보내지 않음
- APNs token을 Firebase에 연결하지 않음
- `deviceId`를 매 앱 실행마다 새로 생성함
- Silent push를 기대하면서 `Background Modes -> Remote notifications`를 켜지 않음

## 13. iOS 팀 전달용 핵심 요구사항

아래 항목만 지켜도 1차 연동은 가능하다.

1. 같은 Firebase 프로젝트를 사용한다.
2. APNs Auth Key를 Firebase Console에 업로드한다.
3. Xcode에서 `Push Notifications` capability를 추가한다.
4. Firebase Messaging SDK를 설치하고 `FirebaseApp.configure()`를 호출한다.
5. 알림 권한 요청 후 `registerForRemoteNotifications()`를 호출한다.
6. FCM token을 로그인 직후와 토큰 갱신 시마다 서버에 등록한다.
7. 푸시 수신 시 `notificationId`, `notificationType`, `deeplink`로 라우팅한다.

## 14. 참고 링크

- Firebase iOS 설정: <https://firebase.google.com/docs/ios/setup>
- Firebase Cloud Messaging iOS 시작: <https://firebase.google.com/docs/cloud-messaging/ios/get-started>
- Firebase Cloud Messaging iOS 수신 처리: <https://firebase.google.com/docs/cloud-messaging/ios/receive-messages>
- Apple APNs Auth Key 안내: <https://developer.apple.com/help/account/capabilities/communicate-with-apns-using-authentication-tokens>
- Apple 알림 권한 요청: <https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications>
- Apple background remote notifications: <https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app>
