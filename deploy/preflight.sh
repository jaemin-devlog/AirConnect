#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MIN_AVAILABLE_MB="${AIRCONNECT_MIN_AVAILABLE_MB:-1536}"
MIN_DISK_MB="${AIRCONNECT_MIN_DISK_MB:-4096}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf '필수 명령어가 없습니다: %s\n' "$1" >&2
        exit 1
    fi
}

for command_name in docker git curl gzip flock; do
    require_command "$command_name"
done

if [[ ! -f .env ]]; then
    printf '.env 파일을 찾을 수 없습니다: %s/.env\n' "$ROOT_DIR" >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    printf 'Docker 데몬에 연결할 수 없습니다.\n' >&2
    exit 1
fi

docker compose --profile green config --quiet

if ! git diff --quiet || ! git diff --cached --quiet; then
    printf 'Git 작업 트리에 추적 중인 변경사항이 있습니다. 배포 전에 정리해주세요.\n' >&2
    exit 1
fi

available_mb="$(awk '/MemAvailable:/ {printf "%d", $2 / 1024}' /proc/meminfo)"
disk_mb="$(df -Pm "$ROOT_DIR" | awk 'NR == 2 {print $4}')"

printf '사용 가능한 메모리: %s MB\n' "$available_mb"
printf '사용 가능한 디스크: %s MB\n' "$disk_mb"

if (( available_mb < MIN_AVAILABLE_MB )); then
    printf 'Blue/Green 동시 실행에 필요한 여유 메모리가 부족합니다. 기준: %s MB\n' "$MIN_AVAILABLE_MB" >&2
    exit 1
fi

if (( disk_mb < MIN_DISK_MB )); then
    printf '이미지 빌드와 DB 백업에 필요한 디스크가 부족합니다. 기준: %s MB\n' "$MIN_DISK_MB" >&2
    exit 1
fi

mysql_container="$(docker compose ps -q mysql)"
redis_container="$(docker compose ps -q redis)"

if [[ -z "$mysql_container" || "$(docker inspect --format '{{.State.Health.Status}}' "$mysql_container")" != "healthy" ]]; then
    printf 'MySQL 컨테이너가 healthy 상태가 아닙니다.\n' >&2
    exit 1
fi

if [[ -z "$redis_container" || "$(docker inspect --format '{{.State.Running}}' "$redis_container")" != "true" ]]; then
    printf 'Redis 컨테이너가 실행 중이 아닙니다.\n' >&2
    exit 1
fi

printf 'Blue/Green 배포 사전 점검을 통과했습니다.\n'
