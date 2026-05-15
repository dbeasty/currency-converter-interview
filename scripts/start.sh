#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ── helpers ────────────────────────────────────────────────────────────────────
log()  { echo "[start] $*"; }
fail() { echo "[start] ERROR: $*" >&2; exit 1; }

# ── parse flags ────────────────────────────────────────────────────────────────
DB_ONLY=false
DETACH=false

for arg in "$@"; do
  case $arg in
    --db-only) DB_ONLY=true ;;
    -d|--detach) DETACH=true ;;
    -h|--help)
      echo "Usage: ./scripts/start.sh [--db-only] [-d]"
      echo ""
      echo "  --db-only   Start only the PostgreSQL container"
      echo "  -d          Run containers in the background (detached)"
      exit 0
      ;;
    *) fail "Unknown argument: $arg" ;;
  esac
done

# ── check docker is running ────────────────────────────────────────────────────
docker info > /dev/null 2>&1 || fail "Docker is not running. Please start Docker Desktop and try again."

# ── build JAR (skip when db-only) ─────────────────────────────────────────────
if [ "$DB_ONLY" = false ]; then
  log "Building JAR with Gradle..."
  (cd "$REPO_ROOT/api/currencyconverter" && ./gradlew bootJar --quiet)
  log "JAR built: api/currencyconverter/build/libs/currencyconverter-0.0.1-SNAPSHOT.jar"
fi

# ── start containers ───────────────────────────────────────────────────────────
DETACH_FLAG=""
[ "$DETACH" = true ] && DETACH_FLAG="-d"

if [ "$DB_ONLY" = true ]; then
  log "Starting database only..."
  docker compose -f "$REPO_ROOT/docker-compose.db.yml" up $DETACH_FLAG
else
  log "Starting full stack (api + db)..."
  docker compose -f "$REPO_ROOT/docker-compose.yml" up --build $DETACH_FLAG
fi
