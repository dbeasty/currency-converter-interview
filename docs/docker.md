# Docker Setup

The project runs as two containers orchestrated by Docker Compose:

| Service | Image | Port |
|---------|-------|------|
| `api` | Built from `api/currencyconverter/Dockerfile` | 8080 |
| `db` | `postgres:17-alpine` | 5432 |

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running (whale icon in menu bar must be active)
- Java 21 (for the Gradle build step)

---

## Running with the start script

The simplest way to run the project is via the provided script from the repo root:

```bash
# Full stack — builds the JAR, then starts api + db
./scripts/start.sh

# Database only — skips the Gradle build, just starts PostgreSQL
./scripts/start.sh --db-only

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

If you prefer to run commands yourself:

**1. Build the JAR**

```bash
cd api/currencyconverter
./gradlew bootJar
cd ../..
```

This produces `api/currencyconverter/build/libs/currencyconverter-0.0.1-SNAPSHOT.jar`, which the Dockerfile copies into the image.

**2. Set up credentials**

The `.env` file at the repo root is git-ignored. Create it (or edit the existing one):

```
POSTGRES_DB=currencyconverter
POSTGRES_USER=ccuser
POSTGRES_PASSWORD=changeme
```

**3. Start the stack**

```bash
# Full stack
docker compose up --build

# Database only
docker compose -f docker-compose.db.yml up
```

The `api` service waits for PostgreSQL to pass its health check before starting. Liquibase runs migrations automatically on first boot.

**4. Verify**

```
GET http://localhost:8080/transactions
```

---

## Credentials

Credentials live in `.env` at the repo root and are git-ignored:

```
POSTGRES_DB=currencyconverter
POSTGRES_USER=ccuser
POSTGRES_PASSWORD=changeme
```

Both the `db` and `api` containers read this file. The `api` container maps the values to `SPRING_DATASOURCE_*` environment variables, which Spring Boot uses to override the H2 defaults baked into `application.properties` / `application.yml`.

### Planned: HashiCorp Vault

When migrating to Vault, replace the `.env` values with one of:

- **Vault Agent** — inject a rendered `.env` at container startup
- **Spring Cloud Vault** — add `spring-cloud-starter-vault-config`; point `spring.cloud.vault.*` at your Vault server and map credentials to the same `spring.datasource.*` keys

---

## Container details

### `api`

- Base image: `eclipse-temurin:21-jre-alpine`
- Copies `build/libs/currencyconverter-0.0.1-SNAPSHOT.jar` → `/app/app.jar`
- H2 console is explicitly disabled (`SPRING_H2_CONSOLE_ENABLED=false`)
- Liquibase applies all pending changesets against PostgreSQL on startup

### `db`

- `postgres:17-alpine` with a named volume (`postgres_data`) for persistence
- Health check: `pg_isready` polled every 10 s — `api` will not start until this passes
- Port `5432` is exposed to the host in both Compose files

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
