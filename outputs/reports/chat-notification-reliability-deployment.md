# 채팅·알림 안정성 수정 배포

## 적용 내용

- 채팅 메시지와 전달 작업을 함께 DB에 저장합니다. 알림 생성 실패는 이미 저장된 채팅을 취소하지 않으며, 전달 작업을 재시도합니다.
- 실시간 메시지·읽음·채팅방 목록 전달에 실패하면 작업을 보관합니다. 서버 재시작 후에도 재시도하며, 같은 전달 경로는 이전 작업부터 처리합니다.
- 재시도 시 삭제된 메시지 내용을 가리고, 나간 사용자에게 새 채팅 Push를 만들지 않습니다. 채팅방 목록은 현재 DB 상태를 사용합니다.
- Push 권한·기기 소유권·채팅방 접근을 잠금 상태에서 확인하고 비동기 FCM 발송을 시작합니다. 응답을 기다리는 동안 DB 잠금은 유지하지 않습니다.
- 이전 발송의 늦은 응답이 새 재시도 상태나 갱신된 기기 토큰을 덮어쓰지 않도록 발송 식별자를 검증합니다.
- Push 처리와 채팅 재시도는 별도 스케줄러에서 실행합니다.

## 앱 계약

기존 API 주소·요청·응답·알림 유형·딥링크는 유지합니다. 외부 전송 성공 직후 프로세스가 종료되면 재전달될 수 있으므로, 앱은 기존 `messageId`와 `notificationId`로 중복 표시를 방지해야 합니다. 읽음 이벤트는 동일 메시지에 대해 여러 번 올 수 있으므로 최신 상태로 갱신합니다. 서버 재시도는 기기의 실제 수신 확인을 대체하지 않습니다. 재연결·화면 재진입 시 기존 REST 목록 조회도 유지합니다.

발송이 시작된 뒤에는 FCM이 이미 접수한 알림을 취소할 수 없습니다. 나가기·삭제·계정 전환에 대한 검증은 발송 시작 직전에 수행합니다.

## 서버 적용 명령

`develop` 반영 후 PuTTY에서 실행합니다. DB 백업·마이그레이션 중 앱이 잠시 중단됩니다. 실패 시 다음 단계로 넘어가지 않습니다.

```bash
(
set -e
cd ~/AirConnect
git fetch origin
git checkout develop
git pull --ff-only origin develop
git log -1 --oneline

docker compose --env-file .env -f docker-compose.yml build airconnect-app
docker compose --env-file .env -f docker-compose.yml stop airconnect-app

umask 077
CHAT_BACKUP_FILE="backup-chat-delivery-$(date +%Y%m%d-%H%M%S).sql"
docker compose --env-file .env -f docker-compose.yml exec -T mysql \
  sh -c 'exec mysqldump --single-transaction --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "$CHAT_BACKUP_FILE"
test -s "$CHAT_BACKUP_FILE"
echo "DB backup: $CHAT_BACKUP_FILE"

docker compose --env-file .env -f docker-compose.yml exec -T mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  < src/main/resources/sql/chat_delivery_reliability_migration_mysql.sql

docker compose --env-file .env -f docker-compose.yml up -d --no-deps airconnect-app
docker compose --env-file .env -f docker-compose.yml ps
curl --retry 30 --retry-delay 2 --retry-connrefused --retry-all-errors --max-time 5 \
  -fsS http://127.0.0.1:8080/api/v1/maintenance
echo
)
```

```bash
cd ~/AirConnect
docker compose --env-file .env -f docker-compose.yml logs --no-color --tail=200 airconnect-app
```

마이그레이션은 `chat_delivery_events`와 `notification_outbox.dispatch_token`을 추가합니다. 기존 메시지·알림은 삭제하지 않습니다. 운영 DB 적용은 별도 작업입니다.

기동 후 테스트 계정 두 개로 채팅 전송, 읽음, 메시지 삭제, 백그라운드 Push를 확인합니다. 실패가 지속되면 `Chat delivery retained for retry`, `After-commit action failed`, `Notification outbox dispatch` 로그와 `chat_delivery_events` 잔여 작업을 확인합니다.

이 변경은 채팅·알림 안정성 수정입니다. 별도 작업 폴더에 남아 있는 1:1 매칭 추가 수정 및 그 마이그레이션을 포함하지 않습니다.
