# Architecture

## System Overview

The currency converter is a single-service backend that stores USD purchase transactions and returns them converted to a requested foreign currency. It integrates with the U.S. Treasury Fiscal Data API for exchange rates and persists everything in PostgreSQL.

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
│  TransactionController ──► CurrencyConversionService   │
│                                   │                     │
│                          ┌────────┴────────┐            │
│                          ▼                 ▼            │
│               ExchangeRateService    TransactionService │
│                    │    │                  │            │
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
│   transactions  ←──  conversion_records  ──►  exchange_rates │
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
| Database | PostgreSQL 17 | Full SQL, JSONB if needed; H2 compatibility for local/test use |
| Migrations | Liquibase | Declarative YAML changesets; rollback support; DB-agnostic |
| Caching | Spring `@Cacheable` + Caffeine | In-process cache avoids redundant Treasury API calls |
| HTTP client | Spring `RestClient` | Synchronous; simple for single external dependency |
| Containerisation | Docker Compose | Reproducible local environment; production-ready with one flag change |

## Deployment Topology

```
Repo root
├── docker-compose.yml          ← full stack (api + db)
├── docker-compose.db.yml       ← database only
├── api/currencyconverter/
│   ├── Dockerfile              ← copies pre-built JAR
│   └── Dockerfile.tests        ← Python test image
└── scripts/
    └── start.sh                ← build JAR + start Docker
```

See [docker.md](docker.md) for full deployment instructions, [data-design.md](data-design.md) for tables, columns, and type decisions, [service-design.md](service-design.md) for HTTP endpoints and request/response behavior, and [testing.md](testing.md) for test tiers and how to run them.
