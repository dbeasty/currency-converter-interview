# Docker Setup

The project runs as two containers orchestrated by Docker Compose:

| Service | Image | Port |
|---------|-------|------|
| `api` | Built from `api/currencyconverter/Dockerfile` | 8080 |
| `db` | `postgres:17-alpine` | 5432 |

## Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- A completed Gradle build (produces the JAR the `api` image copies)

## Quick Start

**1. Build the JAR**

```bash
cd api/currencyconverter
./gradlew bootJar
cd ../..
```

**2. Create your `.env` file**

Copy the example and set your credentials:

```bash
cp .env.example .env   # if an example exists, otherwise edit .env directly
```

The file lives at the repo root and is git-ignored. See [Credentials](#credentials) below.

**3. Start the stack**

```bash
docker compose up --build
```

The `api` service will wait for PostgreSQL to pass its health check before starting.

**4. Verify**

```
GET http://localhost:8080/transactions
```

## Credentials

Credentials are stored in `.env` at the repo root:

```
POSTGRES_DB=currencyconverter
POSTGRES_USER=ccuser
POSTGRES_PASSWORD=changeme
```

> **Note:** These are passed to both the `db` and `api` containers via `env_file`. The `api` container maps them to `SPRING_DATASOURCE_*` environment variables, which Spring Boot uses to override the H2 defaults in `application.properties` / `application.yml`.

### Planned: HashiCorp Vault

The credential keys above are stable. When migrating to Vault, replace the `.env` values with one of:

- **Vault Agent** — inject a rendered `.env` file at container startup
- **Spring Cloud Vault** — add `spring-cloud-starter-vault-config` and point `spring.cloud.vault.*` at your Vault server; the same `spring.datasource.*` keys can be read from a Vault KV path

## Container Details

### `api`

- Base image: `eclipse-temurin:21-jre-alpine`
- Copies `build/libs/currencyconverter-0.0.1-SNAPSHOT.jar` → `/app/app.jar`
- H2 console is explicitly disabled (`SPRING_H2_CONSOLE_ENABLED=false`)
- Liquibase runs on startup and applies all pending changesets against PostgreSQL

### `db`

- `postgres:17-alpine` with a named volume (`postgres_data`) for persistence
- Health check: `pg_isready` polled every 10 s — `api` will not start until this passes

## Useful Commands

```bash
# Start in the background
docker compose up -d

# Tail logs for the API
docker compose logs -f api

# Connect to PostgreSQL directly
docker compose exec db psql -U ccuser -d currencyconverter

# Stop and remove containers (data volume is preserved)
docker compose down

# Stop and also remove the data volume
docker compose down -v
```
