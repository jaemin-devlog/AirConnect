#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/deploy/runtime"
ACTIVE_CONFIG="$RUNTIME_DIR/active.caddy"
PREVIOUS_CONFIG="$RUNTIME_DIR/previous.caddy"
STATE_FILE="$RUNTIME_DIR/active-slot"
LOCK_FILE="$RUNTIME_DIR/deploy.lock"
PUBLIC_HEALTH_URL="${AIRCONNECT_PUBLIC_HEALTH_URL:-https://airconnect.cloud/actuator/health/readiness}"
HEALTH_TIMEOUT_SECONDS="${AIRCONNECT_HEALTH_TIMEOUT_SECONDS:-180}"
DRAIN_SECONDS="${AIRCONNECT_DRAIN_SECONDS:-300}"
STREAM_CLOSE_DELAY="${AIRCONNECT_STREAM_CLOSE_DELAY:-5m}"
candidate_started=false
routing_changed=false
deployment_completed=false

mkdir -p "$RUNTIME_DIR" "$ROOT_DIR/backups"
cd "$ROOT_DIR"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    printf '다른 배포 작업이 이미 실행 중입니다.\n' >&2
    exit 1
fi

write_upstream_config() {
    local destination="$1"
    local service_name="$2"
    local temporary_file="${destination}.tmp"

    printf '%s\n' \
        "reverse_proxy ${service_name}:8080 {" \
        "    health_uri /actuator/health/readiness" \
        "    health_interval 10s" \
        "    health_timeout 5s" \
        "    fail_duration 30s" \
        "    stream_close_delay ${STREAM_CLOSE_DELAY}" \
        "}" >"$temporary_file"
    mv "$temporary_file" "$destination"
}

write_legacy_upstream_config() {
    local destination="$1"
    local service_name="$2"
    local temporary_file="${destination}.tmp"

    # Blue/Green 적용 전 컨테이너에는 Actuator readiness가 없으므로
    # 첫 전환과 최초 롤백 동안에는 능동 헬스체크를 강제하지 않는다.
    printf '%s\n' \
        "reverse_proxy ${service_name}:8080 {" \
        "    stream_close_delay ${STREAM_CLOSE_DELAY}" \
        "}" >"$temporary_file"
    mv "$temporary_file" "$destination"
}

detect_active_slot() {
    if [[ -f "$STATE_FILE" ]]; then
        local stored_slot
        stored_slot="$(tr -d '[:space:]' <"$STATE_FILE")"
        if [[ "$stored_slot" == "blue" || "$stored_slot" == "green" ]]; then
            printf '%s' "$stored_slot"
            return
        fi
    fi

    if [[ -f "$ACTIVE_CONFIG" ]] && grep -q 'airconnect-app-green:8080' "$ACTIVE_CONFIG"; then
        printf 'green'
    else
        printf 'blue'
    fi
}

service_for_slot() {
    if [[ "$1" == "green" ]]; then
        printf 'airconnect-app-green'
    else
        printf 'airconnect-app'
    fi
}

port_for_slot() {
    if [[ "$1" == "green" ]]; then
        printf '8081'
    else
        printf '8080'
    fi
}

wait_for_healthy_container() {
    local service_name="$1"
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
    local container_id

    container_id="$(docker compose --profile green ps -q "$service_name")"
    if [[ -z "$container_id" ]]; then
        printf '대상 컨테이너를 찾을 수 없습니다: %s\n' "$service_name" >&2
        return 1
    fi

    while (( SECONDS < deadline )); do
        if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$container_id" 2>/dev/null || true)" == "healthy" ]]; then
            return 0
        fi
        sleep 5
    done

    printf '대상 컨테이너가 제한 시간 안에 healthy 상태가 되지 않았습니다: %s\n' "$service_name" >&2
    docker compose --profile green logs --tail=120 "$service_name" >&2 || true
    return 1
}

wait_for_http_health() {
    local health_url="$1"
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))

    while (( SECONDS < deadline )); do
        if curl --fail --silent --show-error "$health_url" | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 5
    done
    return 1
}

reload_caddy() {
    docker compose exec -T caddy caddy validate --config /etc/caddy/Caddyfile
    docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile
}

recover_on_exit() {
    local exit_code=$?
    if [[ "$deployment_completed" == "true" || $exit_code -eq 0 ]]; then
        return
    fi

    trap - EXIT INT TERM
    set +e
    printf '배포가 중단되어 기존 슬롯으로 자동 복구합니다.\n' >&2
    if [[ "$routing_changed" == "true" && -f "$PREVIOUS_CONFIG" ]]; then
        docker compose --profile green start "$active_service" >/dev/null 2>&1
        cp "$PREVIOUS_CONFIG" "$ACTIVE_CONFIG"
        if reload_caddy; then
            printf '%s\n' "$active_slot" >"$STATE_FILE"
        else
            printf 'Caddy 자동 복구에 실패했습니다. 즉시 ./deploy/rollback.sh 를 실행하세요.\n' >&2
        fi
    fi
    if [[ "$candidate_started" == "true" ]]; then
        docker compose --profile green stop --timeout 30 "$target_service" >/dev/null 2>&1
    fi
    exit "$exit_code"
}

backup_database() {
    if [[ "${AIRCONNECT_SKIP_DB_BACKUP:-false}" == "true" ]]; then
        printf 'AIRCONNECT_SKIP_DB_BACKUP=true: DB 백업을 건너뜁니다.\n'
        return
    fi

    local backup_path="$ROOT_DIR/backups/airconnect-$(date +%Y%m%d-%H%M%S).sql.gz"
    umask 077
    docker compose exec -T mysql sh -lc \
        'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers "$MYSQL_DATABASE"' \
        | gzip -c >"$backup_path"
    gzip -t "$backup_path"
    printf 'DB 백업 완료: %s\n' "$backup_path"
}

active_slot="$(detect_active_slot)"
if [[ "$active_slot" == "blue" ]]; then
    target_slot="green"
else
    target_slot="blue"
fi

active_service="$(service_for_slot "$active_slot")"
target_service="$(service_for_slot "$target_slot")"
target_port="$(port_for_slot "$target_slot")"
revision="$(git rev-parse --short=12 HEAD)"
export AIRCONNECT_IMAGE_TAG="git-${revision}"
trap recover_on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '현재 슬롯: %s (%s)\n' "$active_slot" "$active_service"
printf '배포 슬롯: %s (%s)\n' "$target_slot" "$target_service"
printf '배포 이미지: airconnect-app:%s\n' "$AIRCONNECT_IMAGE_TAG"

"$ROOT_DIR/deploy/preflight.sh"
backup_database

docker compose --profile green build "$target_service"
docker compose --profile green up -d --no-deps --force-recreate "$target_service"
candidate_started=true
wait_for_healthy_container "$target_service"
wait_for_http_health "http://127.0.0.1:${target_port}/actuator/health/readiness"
curl --fail --silent --show-error --output /dev/null \
    "http://127.0.0.1:${target_port}/api/v1/statistics/departments/rankings"

if [[ ! -f "$ACTIVE_CONFIG" ]]; then
    write_legacy_upstream_config "$ACTIVE_CONFIG" "$active_service"
fi
cp "$ACTIVE_CONFIG" "$PREVIOUS_CONFIG"

# 첫 적용에서는 Caddy에 runtime mount가 추가되므로 필요할 때만 컨테이너가 재생성된다.
docker compose --profile green up -d --no-deps caddy
write_upstream_config "$ACTIVE_CONFIG" "$target_service"
routing_changed=true

reload_caddy
wait_for_http_health "$PUBLIC_HEALTH_URL"

printf '%s\n' "$target_slot" >"$STATE_FILE"
printf '트래픽 전환 완료. 기존 연결 정리를 위해 %s초 대기합니다.\n' "$DRAIN_SECONDS"
sleep "$DRAIN_SECONDS"

wait_for_http_health "$PUBLIC_HEALTH_URL"

docker compose --profile green stop --timeout 30 "$active_service"
deployment_completed=true
printf '배포 완료: %s -> %s, commit=%s\n' "$active_slot" "$target_slot" "$revision"
