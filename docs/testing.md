# Testing

This document describes how tests are organised and how to run them: JVM tests in the Gradle build, Python HTTP integration tests, and Locust performance tests (headless or UI).

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

## Python API integration tests

### Against a local JVM (`bootRun` or IDE)

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

Some cases call the **live** U.S. Treasury API; they need network access and can fail if Treasury is unreachable. For a manual HTTP CLI (create / convert / smoke), see [integration_tests/README.md](../api/currencyconverter/integration_tests/README.md).

### Against Docker Compose (full stack)

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

---

## Performance tests (Locust)

Locust drives HTTP against `PERF_BASE_URL` (same defaults as integration tests in Docker: `http://api:8080`).

### Via start script

```bash
./scripts/start.sh --perf
```

Starts the stack, waits briefly, then runs the headless script inside the test image.

### Headless inside Docker

```bash
docker compose up -d
docker compose --profile tests run --rm tests bash performance_tests/run_perf.sh
```

### Locust web UI (host browser)

Map Locust’s port (default **8089**) and set `--host` to the API as seen from inside the container:

```bash
docker compose --profile tests run --rm -p 8089:8089 tests \
  locust -f performance_tests/locustfile.py --host http://api:8080
```

Open **http://localhost:8089**.

### Locust on the host (venv)

Install dependencies under `api/currencyconverter/performance_tests`, run `./run_perf.sh` or `locust` with `--host http://localhost:8080` while the app is running. Scenario tags, CSV/HTML output, and tuning options are documented in [performance_tests/README.md](../api/currencyconverter/performance_tests/README.md).

---

## Docker `tests` image

- **Dockerfile:** `api/currencyconverter/Dockerfile.tests` (Python 3.12 slim; copies `integration_tests/` and `performance_tests/`; installs Locust from `performance_tests/requirements.txt`).
- **Compose:** service name `tests`, profile `tests`, not started by plain `docker compose up`. Default service command is `sleep infinity` so `docker compose exec -it tests bash` works after `docker compose --profile tests up -d`.
- **Network:** tests reach the API at `http://api:8080` on the Compose network. Stack-wide container notes: [docker.md](docker.md).

---

## Troubleshooting

- **`bind: address already in use` on port 8080** when running `docker compose … tests`: Compose is trying to start the `api` container, which publishes host `8080`. Stop whatever else listens on 8080 (for example a local `./gradlew bootRun`), or use `--no-deps` and `host.docker.internal` as in the integration section above.
- **PostgreSQL / migrations:** integration tests against Docker assume the `db` service is healthy and Liquibase has run on the `api` container; use `docker compose up -d` and wait for the API to respond before running tests.
