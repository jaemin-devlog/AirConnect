#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/deploy/runtime"
ACTIVE_CONFIG="$RUNTIME_DIR/active.caddy"
PREVIOUS_CONFIG="$RUNTIME_DIR/previous.caddy"
STATE_FILE="$RUNTIME_DIR/active-slot"
LOCK_FILE="$RUNTIME_DIR/deploy.lock"
HEALTH_TIMEOUT_SECONDS="${AIRCONNECT_HEALTH_TIMEOUT_SECONDS:-180}"

cd "$ROOT_DIR"
mkdir -p "$RUNTIME_DIR"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    printf '다른 배포 작업이 이미 실행 중입니다.\n' >&2
    exit 1
fi

if [[ ! -f "$PREVIOUS_CONFIG" ]]; then
    printf '복구할 이전 Caddy 설정이 없습니다.\n' >&2
    exit 1
fi

if grep -q 'airconnect-app-green:8080' "$PREVIOUS_CONFIG"; then
    previous_slot="green"
    previous_service="airconnect-app-green"
    previous_port="8081"
else
    previous_slot="blue"
    previous_service="airconnect-app"
    previous_port="8080"
fi

if [[ -f "$ACTIVE_CONFIG" ]] && grep -q 'airconnect-app-green:8080' "$ACTIVE_CONFIG"; then
    failed_service="airconnect-app-green"
else
    failed_service="airconnect-app"
fi

docker compose --profile green start "$previous_service"
deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
    if curl --fail --silent --show-error \
        "http://127.0.0.1:${previous_port}/actuator/health/readiness" | grep -q '"status":"UP"'; then
        break
    fi
    sleep 5
done

if ! curl --fail --silent --show-error \
    "http://127.0.0.1:${previous_port}/actuator/health/readiness" | grep -q '"status":"UP"'; then
    printf '이전 슬롯이 healthy 상태가 아니어서 트래픽을 전환하지 않았습니다.\n' >&2
    exit 1
fi

current_copy="$RUNTIME_DIR/failed.caddy"
if [[ -f "$ACTIVE_CONFIG" ]]; then
    cp "$ACTIVE_CONFIG" "$current_copy"
fi
cp "$PREVIOUS_CONFIG" "$ACTIVE_CONFIG"
docker compose exec -T caddy caddy validate --config /etc/caddy/Caddyfile
docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
printf '%s\n' "$previous_slot" >"$STATE_FILE"
if [[ "$failed_service" != "$previous_service" ]]; then
    docker compose --profile green stop --timeout 30 "$failed_service"
fi
printf '롤백 완료: 활성 슬롯=%s. 실패 슬롯은 중지했으며 컨테이너 로그는 유지됩니다.\n' "$previous_slot"
