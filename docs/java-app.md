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

Application config lives in two files that Spring Boot merges (properties wins over YAML for conflicting keys):

- `src/main/resources/application.properties` — datasource defaults (H2 for local dev)
- `src/main/resources/application.yml` — Liquibase, JPA dialect, `app.treasury.*`

In Docker, `SPRING_DATASOURCE_*` environment variables override the H2 defaults and point at PostgreSQL — no file changes required. See [docker.md](docker.md) for the full environment variable list.

### `app.treasury.*` properties

| Property | Default | Description |
|----------|---------|-------------|
| `app.treasury.base-url` | `https://api.fiscaldata.treasury.gov/services/api/fiscal_service` | Treasury API base URL |
| `app.treasury.timezone` | `America/New_York` | IANA timezone for cache key date alignment |

---

## Building

```bash
cd api/currencyconverter
./gradlew bootJar        # produces build/libs/currencyconverter-0.0.1-SNAPSHOT.jar
./gradlew test           # runs JUnit tests
./gradlew build          # compile + test + jar
```

The `bootJar` output is what the [Dockerfile](../api/currencyconverter/Dockerfile) copies into the `api` container image.
