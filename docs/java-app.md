# Java Application Design

## Stack

| Item | Value |
|------|-------|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Build tool | Gradle 9 (`bootJar` task produces the runnable JAR) |
| Database access | Spring Data JPA (Hibernate) |
| Schema migrations | Liquibase |
| HTTP client | Spring `RestClient` |
| Caching | Spring `@Cacheable` + Caffeine |
| Base package | `com.limidus.currencyconverter` |

---

## Running the application

From `api/currencyconverter`:

```bash
./gradlew bootRun
```

The app listens on **port 8080** unless you set `server.port`.

### Default datasource (PostgreSQL)

`application.yml` points at a local PostgreSQL instance (`jdbc:postgresql://localhost:5432/currencyconverter` with user `ccuser`). If that database is not running, the app will fail to start unless you switch to H2.

### PostgreSQL in Docker (DB only, app on the host)

You can run **only** the database container and keep using `./gradlew bootRun` on the host. From the **repository root** (where `.env` and `docker-compose.db.yml` live):

```bash
docker compose -f docker-compose.db.yml up -d
```

That publishes **5432** on `localhost`, matching the JDBC URL in `application.yml`. Ensure `.env` defines `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` consistent with `spring.datasource.*` (the same values as in [docker.md](docker.md#credentials) work: `currencyconverter` / `ccuser` / `changeme`).

Then start the API from `api/currencyconverter`:

```bash
./gradlew bootRun
```

Convenience (same Compose file, optional detached mode):

```bash
./scripts/start.sh --db-only -d
```

Stop the database when finished:

```bash
docker compose -f docker-compose.db.yml down
```

### In-memory H2 (no local Postgres)

Activate the Spring profile **`h2`**. That loads `application-h2.yml`, which overrides the datasource to an in-memory H2 database and **enables the H2 web console**.

```bash
./gradlew bootRun --args='--spring.profiles.active=h2'
```

Equivalent:

```bash
export SPRING_PROFILES_ACTIVE=h2
./gradlew bootRun
```

After `./gradlew bootJar`, you can run the same profile on the executable JAR:

```bash
java -jar build/libs/currencyconverter-1.0.0.jar --spring.profiles.active=h2
```

(The exact JAR file name matches the `version` property in `build.gradle`.)

### H2 web console

With profile **`h2`** active, Spring Boot enables the console at **http://localhost:8080/h2-console**. Typical login fields:

| Field | Value |
|-------|--------|
| JDBC URL | `jdbc:h2:mem:testdb` |
| User Name | `sa` |
| Password | `password` |

These match `application-h2.yml`. Liquibase still applies the same changelog to the in-memory database.

**Spring Security:** `SecurityConfig` only permits `/auth/token`, `/actuator/health`, and `/version` without a JWT. The H2 console path (`/h2-console/**`) is **not** opened in code, so the browser UI may return **401** until you add dev-only matchers (or use a desktop H2 client with the JDBC URL above instead of the web UI).

---

## Package Structure

```
com.limidus.currencyconverter/
├── CurrencyconverterApplication.java   Entry point; enables TreasuryProperties binding
│
├── controller/
│   ├── TransactionController.java      POST /transactions, GET /transactions/{id}
│   └── VersionController.java          GET /version (build + git metadata)
│
├── service/
│   ├── CurrencyConversionService.java  Orchestrates conversion: load tx → get rate → persist record
│   ├── TransactionService.java         CRUD for Transaction entities
│   ├── ExchangeRateService.java        3-tier rate lookup (cache → DB → Treasury)
│   ├── TreasuryRateCache.java          @Cacheable wrapper around TreasuryApiClient
│   ├── ExchangeRateCache.java          DB-level rate lookup and upsert
│   └── BulkRateLoader.java             Optional startup pre-warm of exchange_rates table
│
├── client/
│   └── TreasuryApiClient.java          HTTP calls to Treasury Fiscal Data API
│
├── domain/
│   ├── Transaction.java                JPA entity → transactions table
│   ├── ExchangeRate.java               JPA entity → exchange_rates table
│   └── ConversionRecord.java           JPA entity → conversion_records table
│
├── repository/
│   ├── TransactionRepository.java      Spring Data repository for Transaction
│   ├── ExchangeRateRepository.java     Spring Data repository for ExchangeRate
│   └── ConversionRepository.java       Spring Data repository for ConversionRecord
│
├── dto/
│   ├── CreateTransactionRequest.java   Validated inbound payload for POST /transactions
│   ├── TransactionCreatedResponse.java 201 response body
│   ├── ConvertedTransactionResponse.java 200 response body for GET /transactions/{id}
│   └── ErrorResponse.java              Uniform error envelope
│
├── exception/
│   ├── NotFoundException.java          Thrown when a transaction ID is not found (→ 404)
│   ├── InvalidRequestException.java    Thrown when no qualifying rate exists (→ 422)
│   └── GlobalExceptionHandler.java     @RestControllerAdvice; maps exceptions to ErrorResponse
│
└── config/
    ├── TreasuryProperties.java         @ConfigurationProperties(prefix = "app.treasury")
    ├── CacheConfig.java                Caffeine cache manager configuration
    └── RestClientConfig.java           Builds the RestClient bean for Treasury calls
```

---

## Layer Responsibilities

### Controller layer

Thin HTTP adapters. Validate the request (via Bean Validation), delegate to services, and map responses. No business logic lives here.

### Service layer

All business logic. `CurrencyConversionService` is the primary orchestrator:

1. Load the `Transaction` (delegates to `TransactionService`)
2. Resolve the best exchange rate (delegates to `ExchangeRateService`)
3. Compute `convertedAmount = purchaseAmountUsd × rate`
4. Persist a `ConversionRecord`
5. Return the assembled `ConvertedTransactionResponse`

### Domain / Repository layer

Plain JPA entities mapped to PostgreSQL tables. Repositories are Spring Data interfaces — no custom SQL except where a `@Query` is needed for the rate lookup.

See [data-design.md](data-design.md) for the full schema.

### Client layer

`TreasuryApiClient` is the only outbound HTTP caller. It builds the filtered/sorted Treasury API URL and deserialises the JSON response into `TreasuryRateRow` value objects. It has no awareness of caching or persistence — those concerns belong to the service layer.

---

## Treasury Rate Selection

The service filters Treasury rates by `effective_date`, not `record_date`. This is the critical correctness decision for volatile currencies.

**Why it matters:** Treasury publishes mid-quarter amendments for currencies like Argentina-Peso and Turkey-Lira. An amendment keeps the original `record_date` (e.g. `2025-03-31`) but carries a later `effective_date` (e.g. `2025-04-15`). Filtering by `record_date` makes those two rows indistinguishable, leading to non-deterministic results. Filtering by `effective_date` ensures the rate that was actually in force on the purchase date is always selected.

For the full explanation with a concrete example, see [`TREASURY_SERVICE.md`](../api/currencyconverter/TREASURY_SERVICE.md).

---

## Caching

The application uses a three-tier lookup to avoid redundant Treasury API calls:

```
Request for (currency, purchaseDate)
      │
      ├─ Tier 1: Caffeine in-process cache  (keyed by currency + purchaseDate + asOfDate-ET)
      │          Hit → return immediately (no I/O)
      │
      ├─ Tier 2: exchange_rates table in PostgreSQL
      │          Hit → load rate, populate Caffeine cache, return
      │
      └─ Tier 3: Treasury Fiscal Data API (HTTPS)
                 Hit → persist to exchange_rates, populate Caffeine cache, return
                 Miss → return null (no rate in 6-month window); null is NOT cached
```

The cache key includes `asOfDate` in **Eastern Time** (Treasury's publication timezone). This causes an automatic cache miss each time Treasury's calendar day rolls over, ensuring stale rates are never served past their publication date.

See [`TREASURY_SERVICE.md`](../api/currencyconverter/TREASURY_SERVICE.md) for the full caching strategy, timezone handling, and configuration reference.

---

## Date and Timestamp Handling

| Type | Java type | SQL type | Used for |
|------|-----------|----------|---------|
| Calendar dates | `LocalDate` | `DATE` | `transactionDate`, `effectiveDate` — timezone-free |
| Audit timestamps | `Instant` | `TIMESTAMP` (UTC) | `createdAt`, `sourceTimestamp`, `conversionTimestamp` |

`Instant` is always UTC. `LocalDate` carries no timezone — a purchase on `2025-04-01` means that calendar date regardless of where the server runs. See [data-design.md](data-design.md#type-decisions) for the rationale.

---

## Configuration

### Files

| File | Role |
|------|------|
| `src/main/resources/application.yml` | Baseline: PostgreSQL datasource, JPA, Liquibase, logging, Actuator, `app.security.*`, `app.treasury.*` |
| `src/main/resources/application-h2.yml` | Loaded when Spring profile **`h2`** is active: in-memory H2 datasource, H2 dialect, **H2 console enabled** |

There is no `application.properties` in this module; YAML is the single source. You can still override any key with external `.properties` or environment variables using [Spring Boot relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html) (for example `SPRING_DATASOURCE_URL`, `SPRING_PROFILES_ACTIVE`, `APP_TREASURY_BULK_LOAD_ENABLED`).

In Docker, Compose injects `SPRING_DATASOURCE_*` and related variables so the `api` container uses PostgreSQL — see [docker.md](docker.md).

### Spring Boot (`spring.*`)

| Property (YAML) | Default | Description |
|-----------------|---------|-------------|
| `spring.application.name` | `currency-converter` | Registered application name |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/currencyconverter` | JDBC URL (overridden by profile `h2` — see below) |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | JDBC driver (`org.h2.Driver` under profile `h2`) |
| `spring.datasource.username` | `ccuser` | DB user (`sa` under profile `h2`) |
| `spring.datasource.password` | `changeme` | DB password (`password` under profile `h2`) |
| `spring.jpa.database-platform` | `org.hibernate.dialect.PostgreSQLDialect` | Hibernate dialect (`H2Dialect` under profile `h2`) |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate schema mode; Liquibase owns DDL |
| `spring.liquibase.change-log` | `classpath:db/changelog/db.changelog-master.yaml` | Liquibase master changelog |
| `spring.devtools.livereload.enabled` | `false` | Disable DevTools live reload |
| `spring.devtools.restart.enabled` | `false` | Disable DevTools classpath restart |

**Profile `h2` only** (`application-h2.yml` replaces the datasource block and adds):

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:h2:mem:testdb` | In-memory H2 |
| `spring.datasource.driver-class-name` | `org.h2.Driver` | H2 driver |
| `spring.datasource.username` | `sa` | H2 user |
| `spring.datasource.password` | `password` | H2 password |
| `spring.jpa.database-platform` | `org.hibernate.dialect.H2Dialect` | H2 Hibernate dialect |
| `spring.h2.console.enabled` | `true` | Expose **http://localhost:8080/h2-console** |

### Logging (`logging.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `logging.level.org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver` | `ERROR` | Reduces WARN noise from benign 404 paths (e.g. DevTools probes) |

### Actuator (`management.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `management.endpoints.web.exposure.include` | `health` | Only the health endpoint is exposed over HTTP |
| `management.endpoint.health.show-details` | `always` | Health JSON includes full detail |

### Application security (`app.security.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `app.security.jwt-secret` | *(see `application.yml` — dev placeholder)* | HS256 signing secret; **must be at least 32 bytes** in production. Override with `APP_SECURITY_JWT_SECRET` (or equivalent env). |
| `app.security.jwt-expiry-seconds` | `3600` | Access token lifetime in seconds |
| `app.security.clients` | Two in-repo dev clients | List of `{ client-id, client-secret }` pairs used by `POST /auth/token`. Prefer a secrets manager in production; nested lists are easiest to maintain in YAML. |

### Treasury integration (`app.treasury.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `app.treasury.base-url` | `https://api.fiscaldata.treasury.gov/services/api/fiscal_service` | Treasury Fiscal Data API base URL |
| `app.treasury.timezone` | `America/New_York` | IANA zone for cache keys and bulk-load cron evaluation (Treasury publication calendar) |
| `app.treasury.bulk-load-enabled` | `false` | When `true`, pre-fetches rates for the current window on startup and on the cron schedule |
| `app.treasury.bulk-load-cron` | `0 30 9 * * *` | Spring cron expression (evaluated in `app.treasury.timezone`); default once daily at 09:30 ET |

For rate selection and caching behaviour (not separate properties), see [Treasury rate selection](#treasury-rate-selection), [Caching](#caching), and [`TREASURY_SERVICE.md`](../api/currencyconverter/TREASURY_SERVICE.md).

---

## Building

If you prefer to run commands yourself:

```bash
cd api/currencyconverter
./gradlew bootJar        # JAR under build/libs/ (name matches version in build.gradle)
./gradlew test           # JUnit tests
./gradlew build          # compile + test + jar
cd ../..
```

The [Dockerfile](../api/currencyconverter/Dockerfile) copies the `bootJar` output from `build/libs/` into the `api` container image (see `docker-compose` / CI for the expected file name). For Python integration tests, Locust, and Docker orchestration, see [testing.md](testing.md).
