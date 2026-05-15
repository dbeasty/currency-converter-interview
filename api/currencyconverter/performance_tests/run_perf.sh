#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_perf.sh — headless Locust performance test runner
#
# Usage:
#   ./run_perf.sh                          # defaults: 20 users, 60 s, mixed
#   ./run_perf.sh -u 50 -t 120s            # 50 users for 2 minutes
#   ./run_perf.sh --tags store             # write-only scenario
#   ./run_perf.sh --tags read              # read-only scenario
#   PERF_BASE_URL=http://staging:8080 ./run_perf.sh
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

HOST="${PERF_BASE_URL:-http://localhost:8080}"
USERS="${PERF_USERS:-20}"
SPAWN_RATE="${PERF_SPAWN_RATE:-5}"
RUN_TIME="${PERF_RUN_TIME:-60s}"
RESULTS_DIR="results"

mkdir -p "$RESULTS_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
HTML_REPORT="$RESULTS_DIR/report_${TIMESTAMP}.html"
CSV_PREFIX="$RESULTS_DIR/stats_${TIMESTAMP}"

echo "========================================"
echo " Currency Converter — Performance Test"
echo "========================================"
echo "  Host        : $HOST"
echo "  Users       : $USERS"
echo "  Spawn rate  : $SPAWN_RATE/s"
echo "  Duration    : $RUN_TIME"
echo "  HTML report : $HTML_REPORT"
echo "========================================"
echo ""

locust \
    -f locustfile.py \
    --headless \
    --host "$HOST" \
    -u "$USERS" \
    -r "$SPAWN_RATE" \
    --run-time "$RUN_TIME" \
    --html "$HTML_REPORT" \
    --csv "$CSV_PREFIX" \
    "$@"

echo ""
echo "Results written to $RESULTS_DIR/"
echo "Open $HTML_REPORT in a browser for the full report."
