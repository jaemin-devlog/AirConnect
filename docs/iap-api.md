# AirConnect IAP API 명세서

## 개요
- Base URL: `/api/v1/iap`
- 응답 포맷: 공통 `ApiResponse` 포맷 사용
- 인증 필요:
  - 필요: `verify`, `sync`, `query`
  - 불필요: webhook (`/ios/notifications`, `/android/notifications`)

---

## 공통 성공 응답 포맷

```json
{
  "success": true,
  "data": {},
  "traceId": "trace-id"
}
```

## 공통 실패 응답 포맷

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "IAP_ACCOUNT_TOKEN_MISMATCH",
    "message": "appAccountToken 검증에 실패했습니다.",
    "httpStatus": 400,
    "traceId": "trace-id",
    "details": null
  },
  "traceId": "trace-id"
}
```

---

## 1) iOS 거래 검증

### POST `/api/v1/iap/ios/transactions/verify`

### Request

```json
{
  "signedTransactionInfo": "JWS_STRING",
  "transactionId": "2000001234567890",
  "appAccountToken": "11111111-2222-3333-4444-555555555555"
}
```

### Response (GRANTED)

```json
{
  "success": true,
  "data": {
    "transactionId": "2000001234567890",
    "purchaseToken": null,
    "orderId": null,
    "productId": "com.airconnect.tickets.pack10",
    "grantStatus": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "ledgerId": "TICKET_LEDGER_90210",
    "processedAt": "2026-03-30T13:00:01Z"
  },
  "traceId": "trace-id"
}
```

### Response (ALREADY_GRANTED)

```json
{
  "success": true,
  "data": {
    "transactionId": "2000001234567890",
    "purchaseToken": null,
    "orderId": null,
    "productId": "com.airconnect.tickets.pack10",
    "grantStatus": "ALREADY_GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "ledgerId": "TICKET_LEDGER_REF_15",
    "processedAt": "2026-03-30T13:00:01Z"
  },
  "traceId": "trace-id"
}
```

---

## 2) iOS 거래 동기화

### POST `/api/v1/iap/ios/transactions/sync`

### Request

```json
{
  "transactions": [
    {
      "signedTransactionInfo": "JWS_STRING_1",
      "transactionId": "2000001234567891",
      "appAccountToken": "11111111-2222-3333-4444-555555555555"
    },
    {
      "signedTransactionInfo": "JWS_STRING_2",
      "transactionId": "2000001234567892",
      "appAccountToken": "11111111-2222-3333-4444-555555555555"
    }
  ]
}
```

### Response (부분 성공)

```json
{
  "success": true,
  "data": {
    "total": 2,
    "successCount": 1,
    "failureCount": 1,
    "results": [
      {
        "success": true,
        "result": {
          "transactionId": "2000001234567891",
          "purchaseToken": null,
          "orderId": null,
          "productId": "com.airconnect.tickets.pack5",
          "grantStatus": "GRANTED",
          "grantedTickets": 5,
          "beforeTickets": 27,
          "afterTickets": 32,
          "ledgerId": "TICKET_LEDGER_90211",
          "processedAt": "2026-03-30T13:01:01Z"
        },
        "errorCode": null,
        "message": null
      },
      {
        "success": false,
        "result": null,
        "errorCode": "IAP_INVALID_TRANSACTION",
        "message": "유효하지 않은 거래입니다."
      }
    ]
  },
  "traceId": "trace-id"
}
```

---

## 3) iOS 거래 조회

### GET `/api/v1/iap/ios/transactions/{transactionId}`

### Response

```json
{
  "success": true,
  "data": {
    "id": 15,
    "userId": 41,
    "store": "APPLE",
    "productId": "com.airconnect.tickets.pack10",
    "transactionId": "2000001234567890",
    "purchaseToken": null,
    "orderId": null,
    "environment": "SANDBOX",
    "status": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 17,
    "afterTickets": 27,
    "processedAt": "2026-03-30T13:00:01Z",
    "createdAt": "2026-03-30T13:00:00Z"
  },
  "traceId": "trace-id"
}
```

---

## 4) Android 거래 검증

### POST `/api/v1/iap/android/purchases/verify`

### Request

```json
{
  "productId": "com.airconnect.tickets.pack10",
  "purchaseToken": "android-token-xxxxx",
  "orderId": "GPA.1234-5678-9012-34567",
  "packageName": "com.airconnect.app",
  "purchaseTime": "2026-03-30T13:00:00Z"
}
```

### Response (GRANTED)

```json
{
  "success": true,
  "data": {
    "transactionId": "GPA.1234-5678-9012-34567",
    "purchaseToken": "android-token-xxxxx",
    "orderId": "GPA.1234-5678-9012-34567",
    "productId": "com.airconnect.tickets.pack10",
    "grantStatus": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 32,
    "afterTickets": 42,
    "ledgerId": "TICKET_LEDGER_90212",
    "processedAt": "2026-03-30T13:02:01Z"
  },
  "traceId": "trace-id"
}
```

---

## 5) Android 거래 동기화

### POST `/api/v1/iap/android/purchases/sync`

### Request

```json
{
  "purchases": [
    {
      "productId": "com.airconnect.tickets.pack5",
      "purchaseToken": "android-token-1",
      "orderId": "GPA.1111-2222-3333-44444",
      "packageName": "com.airconnect.app"
    },
    {
      "productId": "com.airconnect.tickets.pack10",
      "purchaseToken": "android-token-2",
      "orderId": "GPA.5555-6666-7777-88888",
      "packageName": "com.airconnect.app"
    }
  ]
}
```

### Response

```json
{
  "success": true,
  "data": {
    "total": 2,
    "successCount": 2,
    "failureCount": 0,
    "results": [
      {
        "success": true,
        "result": {
          "transactionId": "GPA.1111-2222-3333-44444",
          "purchaseToken": "android-token-1",
          "orderId": "GPA.1111-2222-3333-44444",
          "productId": "com.airconnect.tickets.pack5",
          "grantStatus": "GRANTED",
          "grantedTickets": 5,
          "beforeTickets": 42,
          "afterTickets": 47,
          "ledgerId": "TICKET_LEDGER_90213",
          "processedAt": "2026-03-30T13:03:01Z"
        },
        "errorCode": null,
        "message": null
      },
      {
        "success": true,
        "result": {
          "transactionId": "GPA.5555-6666-7777-88888",
          "purchaseToken": "android-token-2",
          "orderId": "GPA.5555-6666-7777-88888",
          "productId": "com.airconnect.tickets.pack10",
          "grantStatus": "ALREADY_GRANTED",
          "grantedTickets": 10,
          "beforeTickets": 47,
          "afterTickets": 57,
          "ledgerId": "TICKET_LEDGER_REF_20",
          "processedAt": "2026-03-30T13:04:01Z"
        },
        "errorCode": null,
        "message": null
      }
    ]
  },
  "traceId": "trace-id"
}
```

---

## 6) Android 거래 조회

### GET `/api/v1/iap/android/purchases/{purchaseToken}`

### Response

```json
{
  "success": true,
  "data": {
    "id": 20,
    "userId": 41,
    "store": "GOOGLE",
    "productId": "com.airconnect.tickets.pack10",
    "transactionId": "GPA.1234-5678-9012-34567",
    "purchaseToken": "android-token-xxxxx",
    "orderId": "GPA.1234-5678-9012-34567",
    "environment": "PRODUCTION",
    "status": "GRANTED",
    "grantedTickets": 10,
    "beforeTickets": 32,
    "afterTickets": 42,
    "processedAt": "2026-03-30T13:02:01Z",
    "createdAt": "2026-03-30T13:02:00Z"
  },
  "traceId": "trace-id"
}
```

---

## 7) Apple 서버 알림(Webhook)

### POST `/api/v1/iap/ios/notifications`

### Request

```json
{
  "signedPayload": "APPLE_SERVER_NOTIFICATION_V2_PAYLOAD"
}
```

### Response

```json
{
  "success": true,
  "data": {
    "accepted": true
  },
  "traceId": "trace-id"
}
```

---

## 8) Google RTDN(Webhook)

### POST `/api/v1/iap/android/notifications`

### Request

```json
{
  "message": {
    "data": "BASE64_PUBSUB_PAYLOAD"
  }
}
```

### Response

```json
{
  "success": true,
  "data": {
    "accepted": true
  },
  "traceId": "trace-id"
}
```

---

## 에러 코드
- `IAP_INVALID_PRODUCT`
- `IAP_INVALID_TRANSACTION`
- `IAP_STORE_VERIFY_FAILED`
- `IAP_ENVIRONMENT_MISMATCH`
- `IAP_DUPLICATE_REQUEST`
- `IAP_FORBIDDEN`
- `IAP_UNAUTHORIZED`
- `IAP_APPLE_VERIFY_FAILED`
- `IAP_GOOGLE_VERIFY_FAILED`
- `IAP_ACCOUNT_TOKEN_MISMATCH`
- `IAP_ALREADY_PROCESSED`

