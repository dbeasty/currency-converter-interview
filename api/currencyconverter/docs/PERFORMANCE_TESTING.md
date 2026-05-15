# Performance Testing

Load and throughput testing for the currency converter API using [Locust](https://locust.io).

---

## Setup

Requires Python 3.9+ and the app running locally.

```bash
# Install Locust (once — uses your active pyenv Python)
pip install locust

# Start the API (separate terminal)
./gradlew bootRun
```

---

## Running Tests

All commands run from the `performance_tests/` directory:

```bash
cd performance_tests
```

### Headless (scripted / CI)

```bash
# Defaults: 20 users, spawn 5/s, run 60 seconds
./run_perf.sh

# Custom load
./run_perf.sh -u 50  -t 120s          # 50 users, 2 minutes
./run_perf.sh -u 200 -t 60s           # stress test

# Scenario-specific
./run_perf.sh --tags store            # write-only  (POST /transactions)
./run_perf.sh --tags read             # read-only   (GET  /transactions/{id})

# Target a different host
PERF_BASE_URL=http://staging:8080 ./run_perf.sh
```

Environment variable overrides:

| Variable | Default | Description |
|---|---|---|
| `PERF_BASE_URL` | `http://localhost:8080` | Target host |
| `PERF_USERS` | `20` | Concurrent virtual users |
| `PERF_SPAWN_RATE` | `5` | Users spawned per second |
| `PERF_RUN_TIME` | `60s` | Test duration |

### Interactive dashboard

```bash
locust -f locustfile.py --host http://localhost:8080
# Open http://localhost:8089, set users + spawn rate, click Start
```

### Output

Each headless run writes to `performance_tests/results/` (git-ignored):

- `report_<timestamp>.html` — full HTML report with charts
- `stats_<timestamp>_stats.csv` — per-endpoint request statistics
- `stats_<timestamp>_failures.csv` — failure log

---

## Scenarios

| Class | Activated by | Traffic shape |
|---|---|---|
| `MixedUser` | default | 80 % GETs (convert), 20 % POSTs (create) |
| `StoreOnlyUser` | `--tags store` | 100 % POSTs — measures raw write + DB insert throughput |
| `ReadOnlyUser` | `--tags read` | 100 % GETs — stresses the 3-tier cache under read load |

All users share a thread-safe transaction pool. Each worker seeds one transaction on start-up via `on_start()` so GET requests always have valid IDs to work with from the first task execution.

---

## Benchmark Results

Tests run on a local MacBook against `localhost:8080` (Spring Boot + PostgreSQL both on the same machine). Results represent a conservative lower bound — a dedicated server with the load generator on a separate host would yield higher numbers.

### Run 1 — Baseline (20 users)

```
Users: 20   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 49.9 | 9 ms | 5 ms | 12 ms | 19 ms | 790 ms | 0 % |
| POST /transactions | 13.2 | 6 ms | 5 ms | 12 ms | 20 ms | 35 ms | 0 % |
| **Aggregated** | **63.0** | **8 ms** | **5 ms** | **12 ms** | **20 ms** | **650 ms** | **0 %** |

> The p99.9 spike to 790 ms on GETs reflects the first cold-miss requests that fell through to the Treasury Fiscal Data API. This is expected on a cold cache and disappears entirely on subsequent runs.

---

### Run 2 — Medium load (~100 users)

```
Users: ~100   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 355.4 | 2 ms | 2 ms | 6 ms | 9 ms | 19 ms | 0 % |
| POST /transactions | 92.1 | 2 ms | 2 ms | 7 ms | 12 ms | 28 ms | 0 % |
| **Aggregated** | **447.5** | **2 ms** | **2 ms** | **6 ms** | **10 ms** | **21 ms** | **0 %** |

> Cache warm-up from more concurrent users eliminated Treasury API cold misses. p50 dropped from 5 ms → 2 ms and p99.9 dropped from 790 ms → 21 ms.

---

### Run 3 — High load (~120 users)

```
Users: ~120   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 404.9 | 2 ms | 2 ms | 6 ms | 9 ms | 16 ms | 0 % |
| POST /transactions | 103.8 | 2 ms | 2 ms | 7 ms | 12 ms | 29 ms | 0 % |
| **Aggregated** | **508.8** | **2 ms** | **2 ms** | **6 ms** | **10 ms** | **19 ms** | **0 %** |

> Throughput crossed 500 req/s with the worst single request across 30,446 total at 35 ms. Latency distribution remained flat as load increased — a sign the service is not yet saturated.

---

## Summary

| Metric | Value |
|---|---|
| Peak throughput (local, single node) | **509 req/s** |
| Median response time (warm cache) | **2 ms** |
| p95 response time | **6 ms** |
| p99 response time | **10 ms** |
| p99.9 response time (warm cache) | **19 ms** |
| Max observed response time (35k reqs) | **35 ms** |
| Failure rate across all runs | **0 %** |

---

## Interpreting Results

**Why does p50 improve as load increases?**
The in-process `@Cacheable` store fills up faster with more concurrent users. After the first request for a given `(currency, purchaseDate, asOfDate)` key the result is served from JVM heap — zero DB I/O, zero network. Higher concurrency means the cache warms up within the first few seconds of the run rather than gradually.

**What causes the p99.9 spike in Run 1?**
Cold-miss requests that found nothing in the cache or DB called the Treasury Fiscal Data API over the public internet (~300–800 ms RTT). This only happens once per unique cache key per day. Enabling `app.treasury.bulk-load-enabled=true` pre-warms the DB on startup and eliminates these spikes entirely.

**What is the actual ceiling?**
At ~120 users both throughput and latency were still stable (no degradation curve). The limiting factor on a laptop is likely the Locust process itself sharing CPU with the JVM and Postgres. A dedicated load generator on a separate host would push the service well beyond 500 req/s.
