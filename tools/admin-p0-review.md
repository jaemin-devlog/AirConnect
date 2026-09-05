# 관리자 P0: 자동 대화 조회와 점검 판정

## 변경 범위

- 방을 열면 사유/날짜 입력 없이 최근 50개를 자동 조회한다. 이전 기록은 메시지 ID 커서로 가져온다. 저장 순서(ID 내림차순)를 사용하며 날짜 제한은 없다.
- 관리자 권한과 현재 ACTIVE 상태를 매 요청 검증한다. 삭제 원문과 삭제 표시를 함께 반환한다.
- 본문 없는 기존 방 목록/기본 상세 계약은 유지한다. 사용자용 Chat API와 membership 검사도 변경하지 않는다.
- 감사 기록 저장/커밋 실패 시 본문을 반환하지 않는다. 기록에는 방, 관리자, 커서, 반환 메시지 번호, 삭제 건수, 결과가 남고 본문은 남지 않는다. 기존 90일 정리 대상 action을 재사용한다.
- 신고에서 조회할 때 신고-방 관계를 매번 검증한다. 서버가 자동 기록하는 조회 목적은 ADMIN_OPERATIONS이며 허위 수동 사유를 만들지 않는다.
- 프론트는 본문을 컴포넌트 메모리에만 보관하고 방 전환/닫기 시 폐기한다. 취소된 이전 응답은 표시하지 않는다. 실패 자동 재시도는 없다.

## API 추가 (DB 마이그레이션 없음)

POST `/api/v1/admin/chat-rooms/{roomId}/message-history`

요청: `{ "beforeId": 123, "size": 50 }`. 첫 요청은 `{}` 가능. beforeId는 선택이며 반드시 해당 방의 실제 메시지여야 한다. size는 1~100.

응답 data: `{ roomId, items, nextBeforeId, hasMore, inspectedAt }`.
`items`는 기존 ChatMessageItem 형식이며 삭제 원문을 포함한다. 응답은 no-store.

POST `/api/v1/admin/reports/{reportId}/evidence-history`

같은 요청에 `roomId`를 추가한다. 신고와 관련 없는 방이면 403.

기존 message-inspections/evidence-inspections API는 호환성을 위해 유지한다. 기존 계약의 사유/범위 제한도 유지되며 새 화면은 새 API만 호출한다. 두 방식 모두 관리자 검증과 감사 기록을 통과한다.

## 참여자 없는 방 조사 결과

현재 두 점검 쿼리는 `hidden_at IS NULL`만 센다. 현재 접속 수, 원래 참여자 수, 누락 발생 원인을 알려주는 수치가 아니다.

| 코드 근거 | 발생 가능한 정상 상태 | 판정 한계 |
| --- | --- | --- |
| `chat/repository/ChatRoomRepository.countRoomsWithoutVisibleMembers` | 전원 숨김이면 멤버 행이 있어도 0명 | 멤버 행 부재와 구분 못 함 |
| `chat/repository/ChatRoomMemberRepository.countPersonalRoomsWithInvalidVisibleMemberCount` | 1명이 방을 숨기면 보이는 수는 1명 | 실제 멤버 2명이 있어도 기존 오류 발생 |
| `chat/service/ChatService.leaveRoom` | 퇴장 메시지 후 membership 삭제, 방과 대화는 유지 | 전원 퇴장과 생성 실패를 구분 못 함 |
| `groupmatching/service/GMatchingService` 최종방 전환/취소 | 임시팀 CLOSED/CANCELLED 후 임시방 memberships 제거 | 종료된 임시방이 비어 있는 것은 정상 흐름 |

따라서 두 항목을 FAIL에서 WARN으로 변경하고 원인 확인이 필요하다고 표시한다. 숫자를 임의로 줄이거나 해당 방을 삭제/복구하지 않는다. 실제 참조 누락과 티켓 계산 불일치 등 나머지 점검은 기존 FAIL을 유지한다.

운영 화면의 11건/7건 각각의 원인은 이번 로컬 조사로 확정하지 않았다. 운영 DB를 조회하지 않았다. 다음 단계에서는 방별로 전체/숨김 membership 수, 원래 1:1 참여자, 임시팀 상태, 최종방 연결을 비교하는 읽기 전용 상세가 필요하다. 강제 멤버 복원은 종료된 방에 접근 권한을 다시 줄 수 있어 추가 정책 없이 실행하면 안 된다.

## 배포 순서

1. 백엔드 새 API를 먼저 반영한다. 이번 P0 자체에는 DDL 변경이 없다. 기존 6b8a7dc 이전 서버는 앞선 관리자 마이그레이션을 별도로 완료해야 한다.
2. 백엔드 적용 확인 후 프론트 배포. 새 프론트를 먼저 배포하면 message-history가 404일 수 있다.
3. 로그인 → 채팅방 → 최근 대화 → 이전 기록 → 다른 방 전환을 확인한다. 운영 대화 조회는 운영자가 수행한다.

기준 브랜치 정리는 배포와 별개다. 소스 병합/태그가 가비아 서버 컨테이너를 자동으로 바꾸지 않는다. 기존 사용자 로컬 브랜치와 staged Figma 파일은 이 작업에 포함하지 않는다.

## 검증 결과 (2026-09-06)

| 영역 | 통과 | 실패/건너뜀 |
| --- | ---: | ---: |
| 관리자 (H2/단위) | 261 | 0 |
| Chat (Phase 1/2 보안 포함) | 57 | 0 |
| GroupMatching (2:2/3:3 포함) | 39 | 0 |
| Maintenance | 19 | 0 |
| MySQL 8.0.46 + 실제 HTTP/JWT | 22 | 0 |
| STOMP | 6 | 0 |
| 합계 | 404 | 0 |

실행: `gradlew -I tools/report-mysql-verification.init.gradle -PadminReleaseVerification test --tests 'univ.airconnect.admin.*' --tests 'univ.airconnect.chat.*' --tests 'univ.airconnect.groupmatching.*' --tests 'univ.airconnect.maintenance.*' --tests 'univ.airconnect.global.security.stomp.*'`.

MySQL은 전용 `airconnect-p0-mysql`, loopback 13389, tmpfs 저장소, `airconnect_admin_verify` DB만 사용했다. 첫 실행의 실패 3개는 MySQL 오류 1419(실패 주입용 트리거 생성 권한)였다. 일회용 컨테이너에서만 `log_bin_trust_function_creators=1`을 설정하고 같은 전체 묶음을 재실행해 통과했다. 운영 DB 설정은 변경하지 않았다.

프론트 Node 테스트 141/141, TypeScript 검사, 변경 코드 lint, Vite 빌드 통과. 번들 500 kB 초과 경고는 남아 있다. worktree가 ignored build 경로 안에 있어도 유틸리티 CSS를 탐색하도록 app/globals.css에 명시적 source 경로를 추가했다.

브라우저: production API 대신 localhost 18089만 연결하는 전용 프록시(15189), 가상 계정과 MySQL 가상 메시지 사용. 방 상세 열기만으로 50개 표시 → 휴대폰 안을 위로 스크롤 → 63개 표시 확인. 삭제 원문과 `<script>` 문자열이 텍스트로 표시됨, 우측 삭제/전송 시각 확인, 다른 빈 방 전환 시 메시지 0개 및 이전 방 본문 제거 확인.

운영 11건/7건의 실제 원인, 운영 서버 신규 API 적용 및 Firebase 배포 결과는 이 로컬 검증에 포함하지 않는다. 격리 서버의 홈 통계는 mock이므로 홈의 형식 오류는 본 작업에서 운영 장애로 판단하지 않았다.
