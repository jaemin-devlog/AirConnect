# 관리자 P1·P2 운영 진단과 안전 조치

확인일: 2026-09-06. 운영 서버·DB·Redis에는 접속하지 않았고 실제 알림을 보내지 않았다.

## 구현 범위

- 데이터 점검 7개 항목에서 개수만 보여 주던 화면을 실제 대상 목록으로 확장했다.
- 각 행은 채팅방·회원·매칭·알림 작업·티켓 변경 내역 번호와 판정 근거만 반환한다. 메시지 본문, 토큰, 알림 본문은 반환하지 않는다.
- 종료된 임시방·전원 퇴장·숨김 상태는 계속 `WARN`으로 유지한다. 조회 자체가 membership, 채팅방, 티켓, 매칭, 알림을 수정하지 않는다.
- 최종 실패한 알림은 관리자가 확인 후 다시 전송 대기열에 넣을 수 있다. 원본 알림이 있고 실패 당시와 동일한 활성 기기·토큰·알림 권한일 때만 허용한다.
- 같은 알림 작업의 동시 재요청은 DB 행 잠금으로 직렬화한다. 먼저 처리된 요청만 FAILED→PENDING으로 바꾸며 다음 요청은 이미 대기 중이라는 결과를 받는다.
- 알림 재시도와 감사 기록은 같은 트랜잭션이다. 감사 기록 저장 실패 시 상태 변경도 롤백된다.
- 관리자 작업 기록은 수행자·대상 종류·대상 번호를 함께 필터링할 수 있다.

## API

- `GET /api/v1/admin/operations/integrity/{key}?page=0&size=20`
- `POST /api/v1/admin/operations/outbox/{outboxId}/retry`
- `GET /api/v1/admin/audit-logs`에 선택 필드 `targetId` 추가

지원 점검 key: `chat_rooms_without_members`, `personal_rooms_invalid_member_count`,
`accepted_matching_without_chat_room`, `accepted_matching_missing_chat_room_row`,
`outbox_missing_notification`, `ticket_ledger_amount_mismatch`, `ticket_ledger_duplicate_refs`.

## 의도적으로 자동 복구하지 않는 항목

- 참여자를 임의로 다시 만들면 퇴장·차단·종료된 방 접근 권한을 되살릴 수 있다.
- 누락 채팅방을 새로 만들면 이미 생성됐지만 참조만 끊긴 방과 중복될 수 있다.
- 티켓 변경 내역을 수정·삭제하면 어느 잔액이 정답인지 추정하게 된다.
- 원본 알림이 없는 outbox는 보낼 내용을 신뢰할 수 없으므로 재전송하지 않는다.

따라서 위 항목은 대상과 관련 기록을 연결해 사실관계를 확인하도록 했고, 원본을 추정하는 범용 복구 버튼은 만들지 않았다.

## 검증

- 격리 H2에서 7개 상세 SQL의 분류·페이지와 알림 재시도 권한/기기 검증을 실행했다.
- MySQL 8.0.46 일회용 tmpfs 컨테이너에서 그룹 집계·orphan·중복 참조 SQL을 실행했다.
- 관리자·Chat·GroupMatching·Maintenance·STOMP·Auth·IAP 회귀를 실행했다.
- 운영 알림 전송과 운영 데이터 수정은 실행하지 않았다.

PR 생성·병합·운영 배포는 이 단계에 포함하지 않는다.
