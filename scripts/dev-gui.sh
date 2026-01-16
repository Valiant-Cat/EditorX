#!/usr/bin/env bash
set -euo pipefail

# 开发模式：监听源码/资源变更，自动重启 GUI（适合本地开发调试）。
#
# 用法：
#   ./scripts/dev-gui.sh
#
# 可选环境变量：
#   DEV_GUI_POLL_INTERVAL   轮询间隔秒（默认 0.8）
#   DEV_GUI_DEBOUNCE        变更后等待稳定秒（默认 0.6）
#   DEV_GUI_GRADLE_ARGS     透传给 Gradle 的额外参数（默认空）
#   DEV_GUI_TASK            要运行的任务（默认 :gui:run）
#
# 说明：
# - 通过轮询对源码/资源生成快照；检测到变更后会停止当前 GUI 并重启。
# - 使用 --no-daemon 提高“停止/重启”可控性（避免残留 JavaExec）。

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

POLL_INTERVAL="${DEV_GUI_POLL_INTERVAL:-0.8}"
DEBOUNCE="${DEV_GUI_DEBOUNCE:-0.6}"
GRADLE_ARGS="${DEV_GUI_GRADLE_ARGS:-}"
TASK="${DEV_GUI_TASK:-:gui:run}"

GRADLEW="./gradlew"
if [[ ! -x "$GRADLEW" ]]; then
  echo "[dev-gui] 未找到可执行的 ./gradlew，请在项目根目录运行。"
  exit 1
fi

watch_roots=(
  "core/src/main"
  "gui/src/main"
  "i18n-keys/src/main"
  "icons/src/main"
  "plugins"
  "buildSrc/src/main"
  "settings.gradle.kts"
  "gradle.properties"
  "gui/build.gradle.kts"
  "core/build.gradle.kts"
  "i18n-keys/build.gradle.kts"
  "icons/build.gradle.kts"
)

hash_cmd() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256
  else
    sha256sum
  fi
}

stat_line() {
  # macOS: stat -f；Linux: stat -c
  if stat -f "%m %N" "$1" >/dev/null 2>&1; then
    stat -f "%m %N" "$1"
  else
    stat -c "%Y %n" "$1"
  fi
}

list_watch_files() {
  local root
  for root in "${watch_roots[@]}"; do
    if [[ -f "$root" ]]; then
      echo "$root"
      continue
    fi
    [[ -d "$root" ]] || continue

    # 只监控常用开发文件，避免无关大文件导致重启抖动
    find "$root" \
      -type d \( \
        -name ".gradle" -o -name ".idea" -o -name "build" -o -name "out" -o -name "dist" -o -name ".git" \
      \) -prune -o \
      -type f \( \
        -name "*.kt" -o -name "*.kts" -o \
        -name "*.properties" -o -name "*.json" -o -name "*.xml" -o \
        -name "*.yml" -o -name "*.yaml" -o -name "*.svg" \
      \) -print 2>/dev/null
  done
}

snapshot() {
  # 输出一个稳定的哈希，用于判断是否有变更（包含 mtime 与路径）
  list_watch_files \
    | while IFS= read -r f; do
        [[ -e "$f" ]] || continue
        stat_line "$f"
      done \
    | LC_ALL=C sort \
    | hash_cmd \
    | awk '{print $1}'
}

kill_tree() {
  local pid="$1"
  [[ -n "${pid:-}" ]] || return 0
  if ! kill -0 "$pid" >/dev/null 2>&1; then
    return 0
  fi

  # 先杀子进程
  local children
  children="$(pgrep -P "$pid" 2>/dev/null || true)"
  if [[ -n "$children" ]]; then
    local child
    for child in $children; do
      kill_tree "$child"
    done
  fi

  kill -TERM "$pid" >/dev/null 2>&1 || true
}

APP_PID=""

stop_app() {
  if [[ -n "${APP_PID:-}" ]]; then
    echo "[dev-gui] 停止 GUI（pid=${APP_PID}）..."
    kill_tree "$APP_PID" || true
    # 给一点时间让 JavaExec 退出
    sleep 0.6 || true
    kill -KILL "$APP_PID" >/dev/null 2>&1 || true
    wait "$APP_PID" 2>/dev/null || true
    APP_PID=""
  fi
}

start_app() {
  echo "[dev-gui] 启动 GUI：$TASK"
  # 使用 --no-daemon，避免 gradle daemon 残留导致“杀不干净”
  "$GRADLEW" --no-daemon $GRADLE_ARGS "$TASK" &
  APP_PID="$!"
}

restart_app() {
  stop_app
  start_app
}

cleanup() {
  echo ""
  echo "[dev-gui] 退出，清理中..."
  stop_app
}
trap cleanup EXIT INT TERM

echo "[dev-gui] 监听中（轮询 ${POLL_INTERVAL}s，防抖 ${DEBOUNCE}s）..."
last_hash="$(snapshot)"
restart_app

while true; do
  sleep "$POLL_INTERVAL" || true
  current_hash="$(snapshot)"
  if [[ "$current_hash" != "$last_hash" ]]; then
    echo "[dev-gui] 检测到变更，等待稳定 ${DEBOUNCE}s 后重启..."
    sleep "$DEBOUNCE" || true
    last_hash="$(snapshot)"
    restart_app
  fi
done
