# Performance Tests

Project-wide testing (Docker `tests` service, `./scripts/start.sh --perf`): **[docs/testing.md](../../../docs/testing.md)**.

Load and throughput testing for the currency converter API using [Locust](https://locust.io).

## Setup

```bash
cd performance_tests
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
chmod +x run_perf.sh
```

## Running tests

Make sure the app is running first:
```bash
# from the project root
./gradlew bootRun
```

### Quick headless run (defaults: 20 users, 60 s)

```bash
./run_perf.sh
```

### Custom load

```bash
./run_perf.sh -u 50 -t 120s          # 50 concurrent users, 2 minutes
PERF_USERS=100 PERF_RUN_TIME=300s ./run_perf.sh
```

### Scenario-specific runs

```bash
./run_perf.sh --tags store   # write-only: POST /transactions
./run_perf.sh --tags read    # read-only:  GET  /transactions/{id}
```

### Interactive dashboard (browser UI)

```bash
locust -f locustfile.py --host http://localhost:8080
# then open http://localhost:8089
```

## Scenarios

| Class | Default weight | Description |
|---|---|---|
| `MixedUser` | ✅ active | 80 % GETs (convert), 20 % POSTs (create). Realistic production shape. |
| `StoreOnlyUser` | `--tags store` | Only POSTs. Measures raw write + DB insert throughput. |
| `ReadOnlyUser` | `--tags read` | Only GETs. Drives the 3-tier cache (in-process → DB → Treasury API). |

## Results

Each run writes to `results/`:
- `report_<timestamp>.html` — full Locust HTML report with charts
- `stats_<timestamp>_stats.csv` — per-endpoint request stats
- `stats_<timestamp>_failures.csv` — failure details
- `stats_<timestamp>_exceptions.csv` — Python exceptions

> `results/` is git-ignored; check in specific reports manually if needed.

## Interpreting output

At the end of a headless run the script prints a summary line:

```
[PERF] Test complete — requests=1240 failures=2 rps=20.6 p50=48ms p95=320ms p99=980ms
```

Key things to watch:
- **failures > 0**: check `results/*_failures.csv` — could be no-rate-available (422, benign) or real bugs
- **p95 > 1000 ms**: likely hitting the Treasury API live (cold DB); re-run after warm-up
- **p99 >> p95**: outliers suggest cold cache misses going out to Treasury; enable bulk load to warm the DB
