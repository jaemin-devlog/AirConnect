# AirConnect Portfolio Figures Editable

이 파일은 이미지가 아니라 수정 가능한 원본입니다.  
다이어그램은 `Mermaid`, 표는 `Markdown Table`로 작성했습니다.

---

## 1. AirConnect 전체 구조도

```mermaid
flowchart LR
    client["App Client<br/>iOS / Android"]
    server["Spring Boot API Server<br/>클라이언트 요청 수신<br/>스토어 / 광고 검증 요청<br/>주문 / 티켓 / 상태 변경 처리"]

    mysql["MySQL<br/>Order / Ticket / Audit Log"]
    redis["Redis<br/>Reward Session / Refresh Token"]

    appstore["App Store"]
    googleplay["Google Play"]
    admob["AdMob SSV"]

    admin["Admin Console<br/>정합성 점검 / Audit Log / API 통계"]

    client --> server
    server --> mysql
    server --> redis

    server --> appstore
    server --> googleplay
    server --> admob

    server --> admin
```

핵심 문구:

`서버 스펙보다 검증 흐름을 강조하고, 결제 / 광고 / 티켓 / 운영을 같은 상태 변경 구조 안에서 설명`

---

## 2. IAP 결제 검증 시퀀스

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Server
    participant P as App Store / Google Play

    C->>S: 결제 완료 요청
    S->>P: transactionId 기반 검증 요청
    P-->>S: 거래 상태 응답
    S->>S: 거래 고유값 확인
    S->>S: 주문 row lock
    S->>S: Ticket History 저장
    S->>S: User Ticket Balance 반영
    S-->>C: 지급 완료 또는 이미 처리됨
```

---

## 3. IAP 예외 케이스 테스트 표

| 테스트 케이스 | 입력 조건 | 기대 결과 | 실제 결과 |
|---|---|---|---|
| 가짜 서명 영수증 | JWS 서명 / 인증서 위조 | 거절 | 거절 |
| transactionId 불일치 | 요청값과 검증값 불일치 | 거절 | 거절 |
| 사용자 연결값 불일치 | 다른 사용자 토큰 | 거절 | 거절 |
| 환경 불일치 | Sandbox / Production 불일치 | 거절 | 거절 |
| 환불 / 취소 거래 | revocation 정보 포함 | 거절 | 거절 |
| 중복 영수증 | 이미 처리된 거래 | 이미 처리됨 | 이미 처리됨 |

주석:

`만료 거래`는 현재 코드에서 직접 확인한 테스트 근거보다, 실제 검증된 예외 항목 중심으로 정리했습니다.

---

## 4. 동시 요청 레이스 타임라인

```mermaid
sequenceDiagram
    participant A as Request A
    participant B as Request B
    participant O as Order Row

    A->>O: 잠금 획득
    B->>O: 잠금 대기
    A->>A: 지급 처리
    A->>O: commit
    O-->>B: 잠금 해제 후 진입
    B->>B: 이미 지급 상태 확인
    B-->>B: ALREADY_GRANTED
```

설명 포인트:

- 왜 비관적 락을 썼는가
- 유니크 제약만으로 부족했던 이유
- 락 범위가 주문 상태 변경 구간에 걸쳐 있었다는 점
- 동시 2회 요청 테스트에서 `1건 GRANTED / 1건 ALREADY_GRANTED`로 수렴했다는 점

---

## 5. AdMob SSV 상태 전이도

```mermaid
flowchart TD
    ready["READY"]
    rewarded["REWARDED"]
    granted["GRANTED 응답"]
    already["ALREADY_GRANTED 응답"]
    expired["EXPIRED"]

    ready -->|"서명 검증 성공 + 미지급"| rewarded
    rewarded --> granted
    rewarded -->|"이미 REWARDED 상태"| already
    ready -->|"TTL 만료"| expired
```

중앙 문구:

`중복 요청을 막는 것이 아니라 반복 요청에도 재화 상태가 한 번만 바뀌도록 설계`

---

## 6. 티켓 원장 / 잔액 정합성 점검 흐름

```mermaid
flowchart TD
    iap["IAP 지급"]
    admob["AdMob 지급"]
    adminGrant["관리자 지급"]

    ledger["Ticket Transaction 저장"]
    balance["User Ticket Balance 반영"]
    checker["Consistency Checker"]
    console["Admin Console<br/>PASS / FAIL"]

    iap --> ledger
    admob --> ledger
    adminGrant --> ledger

    ledger --> balance
    balance --> checker
    checker --> console
```

### 점검 항목 표

| 점검 항목 | 정상 기준 | 오류 감지 방식 |
|---|---|---|
| 잔액과 이력 합계 일치 | balance = sum(history) | 불일치 시 FAIL |
| 지급 경로별 중복 없음 | refType + refId unique | 중복 시 FAIL |
| IAP 검증 완료 후 미지급 없음 | verified order는 지급 완료 | 미지급 시 FAIL |
| 광고 보상 세션 만료 처리 | TTL 이후 EXPIRED | 미처리 시 FAIL |
| 처리 완료 세션 중복 지급 없음 | REWARDED 1회 | 중복 시 FAIL |

---

## 7. Admin Console 정합성 점검 화면

### 예시 구조

| 점검 항목 | 정상 기준 | 결과 |
|---|---|---|
| 잔액과 이력 합계 일치 | balance = sum(history) | PASS |
| 지급 경로별 중복 이력 없음 | refType + refId unique | PASS |
| IAP 검증 완료 후 미지급 주문 없음 | verified order -> granted | PASS |
| 광고 보상 세션 만료 처리 확인 | TTL 이후 EXPIRED | PASS |
| 처리 완료 세션 중복 지급 없음 | REWARDED 1회 | PASS |
| 환불 반영 후 장부 기록 존재 | refund ledger exists | PASS |
| 운영자 수동 지급 근거 기록 | admin action logged | PASS |

하단 문구:

`불일치 강제 주입 테스트에서도 FAIL 감지가 가능한 구조로 점검 로직을 검증했습니다.`

---

## 8. Admin Console Audit Log / API 통계 화면

### 운영 지표 카드

| 항목 | 값 |
|---|---|
| 누적 Audit Log | 1,047+ |
| 최근 7일 API 호출 | 117건 |
| 고유 엔드포인트 | 24개 |
| 정합성 점검 | 7개 PASS |

### 감사 로그 예시

| 시간 | 액션 | 대상 | 결과 |
|---|---|---|---|
| 10:24 | 정합성 점검 | ticket | PASS |
| 10:27 | 신고 검토 | user 182 | DONE |
| 11:02 | 티켓 조정 | user 74 | DONE |
| 11:36 | API 통계 조회 | dashboard | OK |
| 12:14 | 공지 발송 | all users | DONE |

### API 사용량 예시

| API | 호출 수 |
|---|---:|
| GET /matches | 74 |
| GET /tickets | 58 |
| POST /iap | 46 |
| GET /admin | 39 |
| POST /ads | 31 |

