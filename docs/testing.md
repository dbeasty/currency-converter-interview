# Testing

This document describes how tests are organised and how to run them: JVM tests in the Gradle build, Python HTTP integration tests, and Locust performance tests (headless or UI).

**Docker:** All integration tests, performance tests, and the backing API plus PostgreSQL can be run **entirely via Docker Compose** and the `tests` service image—you do not need Python or Locust installed on the host for those flows. JVM tests run with Gradle (`./gradlew test`); they do not require Docker locally but are commonly executed in CI inside a standard JDK/Gradle container, so the overall test story stays image-based end to end if you want it to be.

---

## Layers

| Layer | Tool | Location |
|-------|------|----------|
| Unit / integration (JVM) | JUnit 5, Mockito | `api/currencyconverter/src/test/` |
| API integration (Python) | `unittest` + `urllib` | `api/currencyconverter/integration_tests/` |
| Performance / load | Locust | `api/currencyconverter/performance_tests/` |

Python suites call a **running** API. They use `INTEGRATION_BASE_URL` / `PERF_BASE_URL` (defaults and Docker values are set below). JWT auth for integration tests uses `INTEGRATION_CLIENT_ID` and `INTEGRATION_CLIENT_SECRET` when the API is configured for OAuth-style clients (see `integration_tests/http_client.py`).

---

## JVM tests (Gradle)

From `api/currencyconverter`:

```bash
./gradlew test           # unit / Spring integration tests only
./gradlew build          # compile + test + bootJar
```

No Docker required for these. See [java-app.md](java-app.md#building) for other Gradle tasks.

---

## Standalone testing (Docker)

The following runs use **Docker Compose** (API, database, and the `tests` image). They are the recommended way to exercise the full stack without installing Python or Locust on your machine.

### API integration tests

Use this when the API runs in the `api` container with PostgreSQL.

**Option A — start script (builds JAR, starts stack, runs integration tests)**

```bash
./scripts/start.sh --test
```

**Option B — one-off test container** (API + DB must already be up; host port **8080** must be free for Compose to start `api` if it is not running)

```bash
docker compose up -d
docker compose --profile tests run --rm tests \
  python3 -m unittest integration_tests.test_api -v
```

Inside Compose, `INTEGRATION_BASE_URL` is `http://api:8080` (see `docker-compose.yml`).

**Option C — long-lived `tests` container + interactive shell**

The `tests` service uses the `tests` Compose profile and runs `sleep infinity` so you can attach repeatedly:

```bash
docker compose --profile tests up -d
docker compose exec -it tests bash
# then, e.g.:
python3 -m unittest integration_tests.test_api -v
```

If the API already runs **on the host** on port 8080 and you do **not** want Compose to start another `api`, use `--no-deps` and point at the host:

```bash
docker compose --profile tests run --rm -it --no-deps \
  -e INTEGRATION_BASE_URL=http://host.docker.internal:8080 \
  -e PERF_BASE_URL=http://host.docker.internal:8080 \
  tests bash
```

Some cases call the **live** U.S. Treasury API; they need network access and can fail if Treasury is unreachable. For a manual HTTP CLI (create / convert / smoke), see [integration_tests/README.md](../api/currencyconverter/integration_tests/README.md).

### Testing with curl

Use these commands for a quick end-to-end smoke test against a running API (`http://localhost:8080`).

```bash
# 1) Health + version
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/version

# 2) Get JWT
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"default-client","clientSecret":"change-me-secret"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 3) Create purchase transaction
curl -s -X POST http://localhost:8080/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"description":"curl smoke","transactionDate":"2024-06-15","purchaseAmountUsd":100.00}'

# 4) Convert by transaction id (replace <TX_ID> with id from create response)
curl -s "http://localhost:8080/transactions/<TX_ID>?countryCurrencyDesc=Canada-Dollar" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: create returns `201`, convert returns `200` with `exchangeRateUsed` and `convertedAmount`.

### Performance and load tests (Locust)

Load and throughput testing for the currency converter API using [Locust](https://locust.io). Locust drives HTTP against `PERF_BASE_URL` (same defaults as integration tests in Docker: `http://api:8080`).

#### Via start script

```bash
./scripts/start.sh --perf
```

Starts the stack, waits briefly, then runs the headless script inside the test image.

#### Headless inside Docker

```bash
docker compose up -d
docker compose --profile tests run --rm tests bash performance_tests/run_perf.sh
```

#### Locust web UI (host browser)

Map Locust’s port (default **8089**) and set `--host` to the API as seen from inside the container:

```bash
docker compose --profile tests run --rm -p 8089:8089 tests \
  locust -f performance_tests/locustfile.py --host http://api:8080
```

Open **http://localhost:8089**.

#### Setup (host / venv, optional)

If you run Locust on the host instead of Docker, use Python 3.9+ and the app reachable at your chosen base URL:

```bash
pip install locust
# Start the API (separate terminal), e.g. from api/currencyconverter:
./gradlew bootRun
```

#### Running tests (from `api/currencyconverter/performance_tests`)

```bash
cd performance_tests
```

**Headless (scripted / CI)**

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

**Interactive dashboard (host)**

```bash
locust -f locustfile.py --host http://localhost:8080
# Open http://localhost:8089, set users + spawn rate, click Start
```

**Output**

Each headless run writes to `performance_tests/results/` (git-ignored):

- `report_<timestamp>.html` — full HTML report with charts
- `stats_<timestamp>_stats.csv` — per-endpoint request statistics
- `stats_<timestamp>_failures.csv` — failure log

#### Scenarios

| Class | Activated by | Traffic shape |
|---|---|---|
| `MixedUser` | default | 80 % GETs (convert), 20 % POSTs (create) |
| `StoreOnlyUser` | `--tags store` | 100 % POSTs — measures raw write + DB insert throughput |
| `ReadOnlyUser` | `--tags read` | 100 % GETs — stresses the 3-tier cache under read load |

All users share a thread-safe transaction pool. Each worker seeds one transaction on start-up via `on_start()` so GET requests always have valid IDs to work with from the first task execution.

More scenario and tuning notes: [performance_tests/README.md](../api/currencyconverter/performance_tests/README.md).

#### Benchmark results

Tests run on a local MacBook against `localhost:8080` (Spring Boot + PostgreSQL both on the same machine). Results represent a conservative lower bound — a dedicated server with the load generator on a separate host would yield higher numbers.

**Run 1 — Baseline (20 users)**

```
Users: 20   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 49.9 | 9 ms | 5 ms | 12 ms | 19 ms | 790 ms | 0 % |
| POST /transactions | 13.2 | 6 ms | 5 ms | 12 ms | 20 ms | 35 ms | 0 % |
| **Aggregated** | **63.0** | **8 ms** | **5 ms** | **12 ms** | **20 ms** | **650 ms** | **0 %** |

> The p99.9 spike to 790 ms on GETs reflects the first cold-miss requests that fell through to the Treasury Fiscal Data API. This is expected on a cold cache and disappears entirely on subsequent runs.

**Run 2 — Medium load (~100 users)**

```
Users: ~100   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 355.4 | 2 ms | 2 ms | 6 ms | 9 ms | 19 ms | 0 % |
| POST /transactions | 92.1 | 2 ms | 2 ms | 7 ms | 12 ms | 28 ms | 0 % |
| **Aggregated** | **447.5** | **2 ms** | **2 ms** | **6 ms** | **10 ms** | **21 ms** | **0 %** |

> Cache warm-up from more concurrent users eliminated Treasury API cold misses. p50 dropped from 5 ms → 2 ms and p99.9 dropped from 790 ms → 21 ms.

**Run 3 — High load (~120 users)**

```
Users: ~120   Spawn rate: 5/s   Duration: 60s   Scenario: mixed
```

| Endpoint | req/s | Avg | p50 | p95 | p99 | p99.9 | Failures |
|---|---|---|---|---|---|---|---|
| GET /transactions/{id} | 404.9 | 2 ms | 2 ms | 6 ms | 9 ms | 16 ms | 0 % |
| POST /transactions | 103.8 | 2 ms | 2 ms | 7 ms | 12 ms | 29 ms | 0 % |
| **Aggregated** | **508.8** | **2 ms** | **2 ms** | **6 ms** | **10 ms** | **19 ms** | **0 %** |

> Throughput crossed 500 req/s with the worst single request across 30,446 total at 35 ms. Latency distribution remained flat as load increased — a sign the service is not yet saturated.

#### Summary

| Metric | Value |
|---|---|
| Peak throughput (local, single node) | **509 req/s** |
| Median response time (warm cache) | **2 ms** |
| p95 response time | **6 ms** |
| p99 response time | **10 ms** |
| p99.9 response time (warm cache) | **19 ms** |
| Max observed response time (35k reqs) | **35 ms** |
| Failure rate across all runs | **0 %** |

#### Interpreting results

**Why does p50 improve as load increases?**  
The in-process `@Cacheable` store fills up faster with more concurrent users. After the first request for a given `(currency, purchaseDate, asOfDate)` key the result is served from JVM heap — zero DB I/O, zero network. Higher concurrency means the cache warms up within the first few seconds of the run rather than gradually.

**What causes the p99.9 spike in Run 1?**  
Cold-miss requests that found nothing in the cache or DB called the Treasury Fiscal Data API over the public internet (~300–800 ms RTT). This only happens once per unique cache key per day. Enabling `app.treasury.bulk-load-enabled=true` pre-warms the DB on startup and eliminates these spikes entirely.

**What is the actual ceiling?**  
At ~120 users both throughput and latency were still stable (no degradation curve). The limiting factor on a laptop is likely the Locust process itself sharing CPU with the JVM and Postgres. A dedicated load generator on a separate host would push the service well beyond 500 req/s.

### Docker `tests` image

- **Dockerfile:** `api/currencyconverter/Dockerfile.tests` (Python 3.12 slim; copies `integration_tests/` and `performance_tests/`; installs Locust from `performance_tests/requirements.txt`).
- **Compose:** service name `tests`, profile `tests`, not started by plain `docker compose up`. Default service command is `sleep infinity` so `docker compose exec -it tests bash` works after `docker compose --profile tests up -d`.
- **Network:** tests reach the API at `http://api:8080` on the Compose network. Stack-wide container notes: [docker.md](docker.md).

---

## Local development (without Docker for the API)

### Python API integration tests against a local JVM (`bootRun` or IDE)

1. Start the app (H2 by default), from `api/currencyconverter`:

   ```bash
   ./gradlew bootRun
   ```

2. Run tests from the same module:

   ```bash
   python3 -m unittest integration_tests.test_api -v
   ```

Optional base URL:

```bash
export INTEGRATION_BASE_URL=http://localhost:8080
python3 -m unittest integration_tests.test_api -v
```

### Locust on the host (venv)

Install dependencies under `api/currencyconverter/performance_tests`, run `./run_perf.sh` or `locust` with `--host http://localhost:8080` while the app is running. Scenario tags, CSV/HTML output, and tuning options are documented in [performance_tests/README.md](../api/currencyconverter/performance_tests/README.md).

---

## Troubleshooting

- **`bind: address already in use` on port 8080** when running `docker compose … tests`: Compose is trying to start the `api` container, which publishes host `8080`. Stop whatever else listens on 8080 (for example a local `./gradlew bootRun`), or use `--no-deps` and `host.docker.internal` as in the standalone integration section above.
- **PostgreSQL / migrations:** integration tests against Docker assume the `db` service is healthy and Liquibase has run on the `api` container; use `docker compose up -d` and wait for the API to respond before running tests.
