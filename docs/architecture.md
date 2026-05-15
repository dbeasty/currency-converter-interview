# Architecture

## System Overview

The currency converter is a single Spring Boot application that packages two cohesive services. The transaction service accepts and persists USD purchase records with validation and a unique identifier. The conversion service retrieves stored transactions, resolves the applicable Treasury exchange rate through a three-level cache (in-process Caffeine → persistent exchange_rates table → Treasury Fiscal Data API), and returns the converted amount; each successful conversion is recorded as an immutable audit entry. Both services share an embedded Tomcat runtime and a common PostgreSQL (or in-memory H2) datastore, while remaining loosely coupled at the code level — a separation that supports future extraction into independent deployable units if external consumers require direct access to conversion or transaction capabilities.


```
┌─────────────────────────────────────────────────────────┐
│                      Client                             │
│           (browser / integration tests / CLI)           │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP :8080
                        ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot API  (api container)           │
│                                                         │
│  TransactionController ──► CurrencyConversionService    │
│                                   │                     │
│                          ┌────────┴────────┐            │
│                          ▼                 ▼            │
│               ExchangeRateService    TransactionService │
│                    │    │                               │
│              cache │    └──► TreasuryApiClient          │
│                    │              │                     │
│                    ▼              │ HTTPS               │
│              ExchangeRateCache    ▼                     │
│                         Treasury Fiscal Data API        │
│                         (external, read-only)           │
│                                                         │
│  JPA / Liquibase                                        │
└───────────────────────┬─────────────────────────────────┘
                        │ JDBC :5432
                        ▼
┌─────────────────────────────────────────────────────────┐
│              PostgreSQL  (db container)                 │
│                                                         │
│   transactions                                          │
│        ▲                                                │
│        │ FK (transaction_id)                            │
│   conversion_records                                    │
│        │ FK (exchange_rate_id)                          │
│        ▼                                                │
│   exchange_rates                                        │
└─────────────────────────────────────────────────────────┘
```

## Components

| Component | Technology | Role |
|-----------|-----------|------|
| API service | Spring Boot 4, Java 21 | HTTP request handling, conversion logic, caching |
| Database | PostgreSQL 17 | Persistent storage for transactions, rates, conversion records |
| Schema migrations | Liquibase | Versioned DDL applied on startup |
| External rate source | Treasury Fiscal Data API | Authoritative USD exchange rates (read-only, public) |
| Container orchestration | Docker Compose | Local development and deployment |

## Request Flow — Currency Conversion

```
GET /transactions/{id}?countryCurrencyDesc=Canada-Dollar
       │
       ▼
TransactionController
       │ load transaction by id (404 if not found)
       ▼
       │
       └─► Persist Transaction 
              │
              ▼
       TransactionCreatedResponse →  200 OK

CurrencyConversionService
       │
       ├─► ExchangeRateService.findMostRecentRate(currency, purchaseDate)
       │         │
       │         ├─ 1. Check in-process Caffeine cache  → hit: return cached rate
       │         ├─ 2. Query exchange_rates table in DB  → hit: return & cache
       │         └─ 3. Call TreasuryApiClient            → fetch, persist, cache
       │
       ├─► Multiply purchaseAmountUsd × exchangeRate → convertedAmount
       │
       └─► Persist ConversionRecord (audit trail)
              │
              ▼
       ConvertedTransactionResponse  →  200 OK
```

## Technology Choices

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Language / runtime | Java 21 | LTS release; virtual threads available if needed |
| Framework | Spring Boot 4 | Standard enterprise Java; auto-configuration reduces boilerplate |
| Database | PostgreSQL 17 | Full SQL, JSONB if needed; H2 compatibility for standalone/local/test use |
| Migrations | Liquibase | Declarative YAML changesets; rollback support; DB-agnostic |
| Caching | Spring `@Cacheable` + Caffeine | In-process cache avoids redundant Treasury API calls |
| HTTP client | Spring `RestClient` | Synchronous; simple for single external dependency |
| Containerisation | Docker Compose | Reproducible local environment; production-ready with one flag change |

## Data Flow

### Store a purchase transaction

```
POST /transactions
        │
        ▼
TransactionController
        │
        ▼
TransactionService
        │  validate (description ≤ 50, date not future, amount positive)
        │  round to 2 decimal places
        │
        └─► persist to transactions table
               │
               ▼
        201 Created  {id, description, transactionDate, purchaseAmountUsd}
```

---

### Retrieve and convert a transaction

```
GET /transactions/{id}?countryCurrencyDesc=Canada-Dollar
        │
        ▼
TransactionController
        │
        ▼
CurrencyConversionService
        │
        ├─► TransactionService ──► transactions table
        │
        └─► ExchangeRateService.findMostRecentRate(currency, purchaseDate)
                │
                ├─► Layer 1: in-process cache (Spring Cache + Caffeine)
                │        hit -> return rate
                │
                ├─► Layer 2: exchange_rates table (persistent rate cache)
                │        hit -> return rate and refresh in-process cache
                │
                └─► Layer 3: Treasury Fiscal Data API
                         hit -> persist in exchange_rates -> cache -> return
                         miss -> conversion unavailable
        │
        └─► persist conversion_records (rate used + converted amount audit)
               │
               ▼
        200 OK  {id, description, transactionDate, purchaseAmountUsd,
                 countryCurrencyDesc, exchangeRateUsed, convertedAmount}
```

## Three-level Caching

The exchange-rate lookup is intentionally layered to reduce Treasury API calls while preserving correctness for historical purchases:

1. **Layer 1 (in-process):** Spring Cache with Caffeine (`treasuryRates`) for fastest repeated reads in the same app instance.
2. **Layer 2 (persistent):** `exchange_rates` table as a durable rate cache across restarts and across requests.
3. **Layer 3 (authoritative source):** Treasury Fiscal Data API when no valid cached/persisted rate is available.

For filter/sort rules (`effective_date`, 6-month window), cache-key behavior, and ET timezone handling, see [TREASURY_SERVICE.md](../api/currencyconverter/TREASURY_SERVICE.md).

## Loading Strategies

The application supports two loading strategies for exchange rates:

- **Fill-on-demand (default):** On a cache miss, lookup falls through DB then Treasury API; successful API responses are persisted and cached for future calls.
- **Optional bulk pre-load/refresh:** A scheduled bulk loader can pre-populate the `exchange_rates` table and refresh it on a cron schedule.

Bulk controls are configured via `app.treasury.bulk-load-enabled` and `app.treasury.bulk-load-cron` (see [java-app.md](java-app.md)).

## Runtime Modes

The service architecture is the same in different runtime modes; only the datasource/profile differs and provides flexibility as how to deploy it based on load and resiliance needs:

- **Docker/Compose mode:** API runs with PostgreSQL backing storage.
- **Standalone host mode:** `h2` profile uses embedded in-memory H2 for zero-setup local runs ( no perma transaction records).

## Three-layer Data Architecture

The data model is organized into three logical layers:

1. **Transaction layer (`transactions`):** source-of-truth USD purchase records.
2. **Rate-cache layer (`exchange_rates`):** fetched Treasury rates keyed by currency/effective date.
3. **Conversion-audit layer (`conversion_records`):** immutable record of each conversion, including rate used and converted amount.

See [data-design.md](data-design.md) for the full schema, constraints, and type decisions.

---

## Security

The API uses a stateless JWT client-credentials flow. Clients exchange a `clientId` and `clientSecret` for a short-lived JWT via `POST /auth/token`; all transaction endpoints require a `Authorization: Bearer <token>` header. There is no session state — each request is independently verified by the JWT filter.

In production, secrets (`jwt-secret`, client credentials, datasource password) are loaded from HashiCorp Vault via Spring Cloud Vault rather than committed to configuration files.

See [security.md](security.md) for the full auth flow, Vault integration details, and production hardening guidance.

---

## Java Application

The service is built with Spring Boot 4 on Java 21. Key implementation areas:

- **Package structure and layer responsibilities** — controller, service, client, domain, repository, config
- **Spring profiles** — `h2` (default, embedded in-memory), `local` (host PostgreSQL), `release` (Vault-backed production)
- **Configuration reference** — all `spring.*`, `app.security.*`, and `app.treasury.*` properties with defaults
- **Caching configuration** — Caffeine cache manager, cache key design, Eastern Time timezone handling

See [java-app.md](java-app.md) for the full implementation guide.

---

## Architectural Limitations

### Exchange rate data source

Rates are published quarterly and may be up to 90 days old; intraday and daily movements are not captured. Real-time currency conversion requires a live market data feed, not a fiscal reporting dataset. Additionally, the rate a financial institution actually charges at the point of transaction is fluid and determined by the institution itself — it incorporates spread, fees, and live market conditions that no static government dataset can reflect.

This implementation satisfies the stated requirements but **is not suitable for a production financial conversion system** where accuracy within days, hours, or at point-of-sale matters.

To replace the data source, swap `TreasuryApiClient` for a commercial provider (e.g. Open Exchange Rates, Fixer.io, XE) — the `ExchangeRateService` interface and three-level cache are designed to accommodate this. See [TREASURY_SERVICE.md](../api/currencyconverter/TREASURY_SERVICE.md) for the full analysis.

---

See [docker.md](docker.md) for runtime/deployment instructions, [service-design.md](service-design.md) for HTTP endpoints and request/response behavior, and [testing.md](testing.md) for test tiers and execution.
