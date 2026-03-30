# AirConnect IAP 구현 가이드

## 개요
- 목적: Apple StoreKit2 / Google Play Billing 검증 후 서버 기준으로 티켓 지급
- 원칙: 클라이언트 전달 티켓 수 불신, `productId -> tickets` 서버 매핑만 사용
- 멱등성: Apple `(store, transactionId)`, Google `(store, purchaseToken)`

## API
- `POST /api/v1/iap/ios/transactions/verify`
- `POST /api/v1/iap/ios/transactions/sync`
- `GET /api/v1/iap/ios/transactions/{transactionId}`
- `POST /api/v1/iap/android/purchases/verify`
- `POST /api/v1/iap/android/purchases/sync`
- `GET /api/v1/iap/android/purchases/{purchaseToken}`
- `POST /api/v1/iap/ios/notifications` (webhook skeleton)
- `POST /api/v1/iap/android/notifications` (webhook skeleton)

## 도메인 구조
- `iap/application`: 처리 오케스트레이션/쿼리/지급
- `iap/domain`: store, status, grant status, product policy
- `iap/apple`, `iap/google`: 스토어별 verifier 구현
- `iap/infrastructure`: properties, payload 보안 유틸
- `iap/repository`: order/event/ledger 저장소

## iOS App Account Token
- `users.ios_app_account_token` 컬럼으로 사용자별 UUID 토큰을 서버가 관리
- 신규 사용자 생성 시 자동 발급, 기존 사용자도 로그인/`/users/me` 호출 시 누락이면 자동 생성
- iOS 검증 시 JWS의 `appAccountToken`과 서버 발급 토큰이 다르면 `IAP_ACCOUNT_TOKEN_MISMATCH`
- Android 검증은 해당 토큰을 사용하지 않음

## 설정
```yaml
iap:
  apple:
    bundle-id: com.airconnect.app
    environment: PRODUCTION
  google:
    package-name: com.airconnect.app
    service-account-json-path: /path/to/google-service-account.json
    verify-enabled: true
```

## 운영 포인트
- raw payload 원문 저장 금지: 해시 + 마스킹만 저장
- traceId + userId + store + transaction/purchase key로 추적
- webhook은 우선 수신/저장 골격만 구현, 환불/회수 후속 작업 필요

## 남은 TODO
- Apple signedTransactionInfo의 x5c 체인 완전 검증 추가
- Google RTDN Pub/Sub 서명 검증 및 비동기 처리 파이프라인 추가
- 환불/취소 시 `ticket_ledger` 역거래(회수) 정책 구현

