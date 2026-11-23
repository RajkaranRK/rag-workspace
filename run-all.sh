#!/usr/bin/env bash
# Control script for the whole rag-workspace stack.
#
#   ./run-all.sh start [service...]    start everything (or only the named services)
#   ./run-all.sh stop  [service...]    stop everything (or only the named services)
#   ./run-all.sh restart [service...]
#   ./run-all.sh status
#   ./run-all.sh logs <service>        tail a service log (Ctrl-C to quit)
#
# Services (start order): ollama postgres reranker demo agent
#   ollama   - local LLM server on :11434 (only started if not already running)
#   postgres - pgvector database via docker compose on :5432
#   reranker - FastAPI reranker on :8001 (creates .venv on first run)
#   demo     - Spring Boot RAG app on :8080
#   agent    - Spring AI agent (embedding-demo/spring-agent) on :8082
#
# Logs and PID files live in ./.run/

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"
mkdir -p "$RUN_DIR"

SERVICES=(ollama postgres kafka reranker demo agent)

OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
OLLAMA_MODELS=("nomic-embed-text" "qwen3:8b")
RERANKER_PORT="${RERANKER_PORT:-8001}"
DEMO_PORT=8080
AGENT_PORT="${PORT:-8082}"
POSTGRES_PORT=5432
KAFKA_PORT=9092
OLLAMA_PORT=11434

# Seconds to wait for each service to open its port
WAIT_OLLAMA=30
WAIT_POSTGRES=60
WAIT_KAFKA=90
WAIT_RERANKER=300   # first start downloads/loads the model
WAIT_DEMO=120
WAIT_AGENT=120

c_green=$'\033[32m'; c_red=$'\033[31m'; c_yellow=$'\033[33m'; c_off=$'\033[0m'
info() { echo "${c_green}==>${c_off} $*"; }
warn() { echo "${c_yellow}!!${c_off}  $*"; }
err()  { echo "${c_red}xx${c_off}  $*" >&2; }

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

wait_for_port() { # name port timeout
  local name=$1 port=$2 timeout=$3 waited=0
  while ! port_open "$port"; do
    if [[ $waited -ge $timeout ]]; then
      err "$name did not open port $port within ${timeout}s (see .run/$name.log)"
      return 1
    fi
    sleep 2; waited=$((waited + 2))
  done
  info "$name is up on port $port"
}

pid_file() { echo "$RUN_DIR/$1.pid"; }
log_file() { echo "$RUN_DIR/$1.log"; }

is_running() { # name
  local f; f="$(pid_file "$1")"
  [[ -f "$f" ]] && kill -0 "$(cat "$f")" 2>/dev/null
}

kill_tree() { # pid signal
  local pid=$1 sig=${2:-TERM} child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child" "$sig"; done
  kill "-$sig" "$pid" 2>/dev/null || true
}

need() { command -v "$1" >/dev/null 2>&1 || { err "'$1' is required but not installed"; return 1; }; }

# Start a background process: name, workdir, command...
spawn() {
  local name=$1 dir=$2; shift 2
  ( cd "$dir" && nohup "$@" >"$(log_file "$name")" 2>&1 & echo $! >"$(pid_file "$name")" )
}

stop_by_pid() { # name
  local name=$1 f pid i
  f="$(pid_file "$name")"
  if ! is_running "$name"; then rm -f "$f"; info "$name not running (pid file)"; return 0; fi
  pid="$(cat "$f")"
  kill_tree "$pid" TERM
  for i in {1..20}; do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
  if kill -0 "$pid" 2>/dev/null; then kill_tree "$pid" KILL; fi
  rm -f "$f"
  info "$name stopped"
}

# ---------------------------------------------------------------- start_*

start_ollama() {
  if port_open "$OLLAMA_PORT"; then info "ollama already running on :$OLLAMA_PORT"
  else
    need ollama || return 1
    info "starting ollama serve"
    spawn ollama "$ROOT" ollama serve
    wait_for_port ollama "$OLLAMA_PORT" "$WAIT_OLLAMA" || return 1
  fi
  local m
  for m in "${OLLAMA_MODELS[@]}"; do
    if ollama list 2>/dev/null | awk 'NR>1{print $1}' | grep -qE "^${m}(:latest)?$"; then
      info "ollama model present: $m"
    else
      warn "ollama model '$m' missing - pulling (may take a while)"
      ollama pull "$m" || { err "failed to pull $m"; return 1; }
    fi
  done
}

start_postgres() {
  need docker || return 1
  if port_open "$POSTGRES_PORT"; then info "postgres already listening on :$POSTGRES_PORT"; return 0; fi
  info "starting postgres (docker compose)"
  ( cd "$ROOT/postgres" && docker compose up -d ) || return 1
  wait_for_port postgres "$POSTGRES_PORT" "$WAIT_POSTGRES" || return 1
  # wait until the container is healthy (pg_isready)
  local i status
  for i in {1..30}; do
    status="$(docker inspect -f '{{.State.Health.Status}}' rag-postgres 2>/dev/null || echo unknown)"
    [[ "$status" == "healthy" ]] && { info "postgres healthy"; return 0; }
    sleep 2
  done
  warn "postgres port is open but health status is '$status'"
}

start_kafka() {
  need docker || return 1
  if port_open "$KAFKA_PORT"; then info "kafka already listening on :$KAFKA_PORT"; return 0; fi
  info "starting kafka (docker compose)"
  ( cd "$ROOT/kafka" && docker compose up -d ) || return 1
  wait_for_port kafka "$KAFKA_PORT" "$WAIT_KAFKA" || return 1
  local i status
  for i in {1..30}; do
    status="$(docker inspect -f '{{.State.Health.Status}}' rag-kafka 2>/dev/null || echo unknown)"
    [[ "$status" == "healthy" ]] && { info "kafka healthy"; return 0; }
    sleep 2
  done
  warn "kafka port is open but health status is '$status'"
}

start_reranker() {
  if is_running reranker; then info "reranker already running"; return 0; fi
  if port_open "$RERANKER_PORT"; then warn "port $RERANKER_PORT already in use, assuming reranker is running"; return 0; fi
  need python3 || return 1
  local dir="$ROOT/reranker-service"
  if [[ ! -d "$dir/.venv" ]]; then
    info "creating reranker virtualenv and installing requirements (first run only)"
    ( cd "$dir" && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt ) \
      >"$(log_file reranker-setup)" 2>&1 || { err "reranker setup failed (see .run/reranker-setup.log)"; return 1; }
  fi
  info "starting reranker on :$RERANKER_PORT (loading model, can take a while)"
  spawn reranker "$dir" .venv/bin/python -m uvicorn app:app --host 0.0.0.0 --port "$RERANKER_PORT"
  wait_for_port reranker "$RERANKER_PORT" "$WAIT_RERANKER"
}

start_maven_service() { # name dir port timeout
  local name=$1 dir=$2 port=$3 timeout=$4
  if is_running "$name"; then info "$name already running"; return 0; fi
  if port_open "$port"; then warn "port $port already in use, assuming $name is running"; return 0; fi
  need mvn || return 1
  need java || return 1
  info "starting $name on :$port"
  spawn "$name" "$dir" mvn -q spring-boot:run
  wait_for_port "$name" "$port" "$timeout"
}

start_demo()  { start_maven_service demo  "$ROOT/demo" "$DEMO_PORT" "$WAIT_DEMO"; }
start_agent() { start_maven_service agent "$ROOT/embedding-demo/spring-agent" "$AGENT_PORT" "$WAIT_AGENT"; }

# ---------------------------------------------------------------- stop_*

stop_ollama()   {
  if is_running ollama; then stop_by_pid ollama
  else info "ollama not started by this script - leaving it running"; fi
}
stop_postgres() { need docker || return 1; ( cd "$ROOT/postgres" && docker compose stop ) && info "postgres stopped (data volume kept)"; }
stop_kafka()    { need docker || return 1; ( cd "$ROOT/kafka" && docker compose stop ) && info "kafka stopped (data volume kept)"; }
stop_reranker() { stop_by_pid reranker; }
stop_demo()     { stop_by_pid demo; }
stop_agent()    { stop_by_pid agent; }

# ---------------------------------------------------------------- status

status_line() { # name port
  local name=$1 port=$2 state
  if port_open "$port"; then state="${c_green}UP${c_off}  "; else state="${c_red}DOWN${c_off}"; fi
  printf '  %-9s %s :%-6s %s\n' "$name" "$state" "$port" "$(is_running "$name" && echo "pid $(cat "$(pid_file "$name")")" || echo "")"
}

cmd_status() {
  echo "Service status:"
  status_line ollama   "$OLLAMA_PORT"
  status_line postgres "$POSTGRES_PORT"
  status_line kafka    "$KAFKA_PORT"
  status_line reranker "$RERANKER_PORT"
  status_line demo     "$DEMO_PORT"
  status_line agent    "$AGENT_PORT"
}

# ---------------------------------------------------------------- dispatch

# Accept folder-style names too (spring-agent, reranker-service, ...)
normalize() {
  case "$1" in
    spring-agent|spring_agent|ollama-reactive-agent) echo agent ;;
    reranker-service|rerank) echo reranker ;;
    rag|rag-app|app) echo demo ;;
    db|pg|pgvector) echo postgres ;;
    broker|kafka-broker) echo kafka ;;
    *) echo "$1" ;;
  esac
}

valid_service() { local s; for s in "${SERVICES[@]}"; do [[ "$s" == "$1" ]] && return 0; done; return 1; }

select_services() { # args -> prints selected (in order)
  if [[ $# -eq 0 ]]; then printf '%s\n' "${SERVICES[@]}"; return; fi
  local s args=()
  for s in "$@"; do args+=("$(normalize "$s")"); done
  for s in "${args[@]}"; do valid_service "$s" || { err "unknown service '$s' (choose from: ${SERVICES[*]})"; exit 2; }; done
  # keep canonical order
  local want=" ${args[*]} "
  for s in "${SERVICES[@]}"; do [[ "$want" == *" $s "* ]] && echo "$s"; done
}

cmd_start() {
  local s
  for s in $(select_services "$@"); do
    "start_$s" || { err "failed to start $s - stopping startup"; return 1; }
  done
  echo; cmd_status
  echo
  echo "RAG API:   POST http://localhost:$DEMO_PORT/api/v1/rag/ask   {\"question\":\"...\"}"
  echo "Agent API: POST http://localhost:$AGENT_PORT/api/agent/chat  {\"message\":\"...\"}"
}

cmd_stop() {
  local s list
  # stop in reverse order
  list=($(select_services "$@"))
  for ((i=${#list[@]}-1; i>=0; i--)); do "stop_${list[i]}"; done
}

cmd_logs() {
  [[ $# -eq 1 ]] || { err "usage: $0 logs <service>"; exit 2; }
  set -- "$(normalize "$1")"
  if [[ "$1" == postgres ]]; then ( cd "$ROOT/postgres" && docker compose logs -f ); return; fi
  if [[ "$1" == kafka ]]; then ( cd "$ROOT/kafka" && docker compose logs -f ); return; fi
  valid_service "$1" || { err "unknown service '$1'"; exit 2; }
  [[ -f "$(log_file "$1")" ]] || { err "no log yet for $1"; exit 1; }
  tail -n 100 -f "$(log_file "$1")"
}

usage() { sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

action="${1:-}"; shift || true
case "$action" in
  start)   cmd_start "$@" ;;
  stop)    cmd_stop "$@" ;;
  restart) cmd_stop "$@"; cmd_start "$@" ;;
  status)  cmd_status ;;
  logs)    cmd_logs "$@" ;;
  *)       usage; exit 1 ;;
esac
