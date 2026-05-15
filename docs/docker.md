# Docker Setup

The core stack is orchestrated by Docker Compose: **Vault** (secrets), **PostgreSQL**, and the **API**. An optional **`tests`** service (Python) runs integration and load tests; see [testing.md](testing.md).

| Service | Image | Port |
|---------|-------|------|
| `vault` | `hashicorp/vault:1.18` (dev mode) | 8200 |
| `vault-init` | `hashicorp/vault:1.18` (one-shot seed) | — |
| `api` | Built from `api/currencyconverter/Dockerfile` | 8080 |
| `db` | `postgres:17-alpine` | 5432 |
| `tests` | `api/currencyconverter/Dockerfile.tests` | (host ports only when published, e.g. Locust 8089) |

**Startup order:** `vault` → `vault-init` (seeds KV) → `db` (healthy) → `api` (reads secrets from Vault with profile `release`).

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running (whale icon in menu bar must be active)
- Java 21 (for the Gradle build step)

---

## Running with the start script

The simplest way to run the project is via the provided script from the repo root:

```bash
# Full stack — builds the JAR, then starts vault + db + api
./scripts/start.sh

# Database only — skips the Gradle build, just starts PostgreSQL
./scripts/start.sh --db-only

# Integration / performance tests (see docs/testing.md)
./scripts/start.sh --test
./scripts/start.sh --perf

# Run in the background (detached)
./scripts/start.sh -d
./scripts/start.sh --db-only -d
```

The script will:
1. Check Docker is running and fail early with a clear message if not
2. Build the JAR via `./gradlew bootJar` (skipped for `--db-only`)
3. Start the appropriate Compose file

---

## Running manually

If you prefer to run commands yourself, build the JAR first — see [Building](java-app.md#building) in the Java application doc — then:

**1. Set up credentials**

Copy [`.env.example`](../.env.example) to `.env` at the repo root (git-ignored):

```
POSTGRES_DB=currencyconverter
POSTGRES_USER=ccuser
POSTGRES_PASSWORD=changeme
VAULT_TOKEN=dev-root-token
```

`POSTGRES_*` values are used by the **`db`** container and copied into Vault by **`vault-init`**. The **`api`** container does not read database credentials from `.env` directly; it loads them from Vault via Spring Cloud Vault.

**2. Start the stack**

```bash
# Full stack
docker compose up --build

# Database only (no Vault or API)
docker compose -f docker-compose.db.yml up
```

The `api` service waits for `vault-init` to finish and PostgreSQL to pass its health check before starting. Liquibase runs migrations automatically on first boot.

**3. Verify**

```
GET http://localhost:8080/actuator/health
POST http://localhost:8080/auth/token
```

Example token request (matches seeded client credentials):

```json
{ "clientId": "default-client", "clientSecret": "change-me-secret" }
```

---

## Credentials

| Variable | Used by | Purpose |
|----------|---------|---------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `db`, `vault-init` | PostgreSQL bootstrap and Vault KV seed |
| `VAULT_TOKEN` | `vault`, `vault-init`, `api` | Dev root token (`VAULT_DEV_ROOT_TOKEN_ID`) |

### HashiCorp Vault (Docker stack)

- **`vault`** runs in dev mode with a fixed root token (`VAULT_TOKEN`, default `dev-root-token`).
- **`vault-init`** runs once per `docker compose up`, enables KV v2 at `secret/` if needed, and writes `secret/currency-converter` with:
  - `spring.datasource.*` (JDBC URL uses host `db` on the Compose network)
  - `app.security.jwt-secret`, `app.security.clients[n].*`
- **`api`** uses `SPRING_PROFILES_ACTIVE=release` and `spring.config.import: vault://` (see `application-release.yml`). API client secrets and the JWT signing key live in the **same KV secret** as the datasource — not separate host directories.

To re-seed after changing `.env`, recreate the init container:

```bash
docker compose rm -sf vault-init && docker compose up vault-init
```

For local CLI access to Vault from the host:

```bash
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=dev-root-token
vault kv get secret/currency-converter
```

---

## Container details

### `vault`

- Dev server (in-memory, auto-unsealed); **not** for production
- Port `8200` published for host debugging

### `vault-init`

- Executes [`scripts/vault-init.sh`](../scripts/vault-init.sh)
- Exits successfully before `api` starts

### `api`

- Base image: `eclipse-temurin:21-jre-alpine`
- Copies `build/libs/currencyconverter-0.0.1-SNAPSHOT.jar` → `/app/app.jar`
- Profile **`release`**: required Vault import; datasource and security from KV
- H2 console is explicitly disabled (`SPRING_H2_CONSOLE_ENABLED=false`)
- Liquibase applies all pending changesets against PostgreSQL on startup

### `db`

- `postgres:17-alpine` with a named volume (`postgres_data`) for persistence
- Health check: `pg_isready` polled every 10 s — `api` will not start until this passes
- Port `5432` is exposed to the host in both Compose files

### `tests`

Python 3.12 image with `integration_tests/` and `performance_tests/` (Locust). Opt in with Compose profile **`tests`**; targets `http://api:8080` on the project network. Commands, `run` vs `exec`, and port **8089** for the Locust UI are in **[testing.md](testing.md)**.

---

## Useful commands

```bash
# Tail logs for the API
docker compose logs -f api

# Connect to PostgreSQL directly
docker compose exec db psql -U ccuser -d currencyconverter

# Stop containers (data volume preserved)
docker compose down

# Stop and delete the data volume (full reset)
docker compose down -v
```
