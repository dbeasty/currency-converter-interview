# Service Design

## Overview

The API exposes two primary operations:

1. **Store a purchase transaction** — accepts a USD amount and returns a persisted record
2. **Retrieve and convert a transaction** — returns the stored transaction with a converted amount in a requested foreign currency

Plus two operational endpoints: a health check and a version/build-info endpoint.

---

## Endpoints

### POST /transactions

Store a new purchase transaction.

**Request**

```
POST /transactions
Content-Type: application/json
```

```json
{
  "description": "Office chair",
  "transactionDate": "2025-03-15",
  "purchaseAmountUsd": 249.99
}
```

**Validation rules**

| Field | Rule |
|-------|------|
| `description` | Required; max 50 characters |
| `transactionDate` | Required; must not be a future date (`@PastOrPresent` — a future date could never have an exchange rate, so this produces a clearer error than waiting for conversion to fail) |
| `purchaseAmountUsd` | Required; must be positive; stored rounded to 2 decimal places |

**Response — 201 Created**

```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "description": "Office chair",
  "transactionDate": "2025-03-15",
  "purchaseAmountUsd": 249.99,
  "createdAt": "2025-03-15T18:00:00Z"
}
```

---

### GET /transactions/{id}

Retrieve a transaction and convert its USD amount to a foreign currency.

**Request**

```
GET /transactions/{id}?countryCurrencyDesc=Canada-Dollar
```

The `countryCurrencyDesc` query parameter must match a Treasury API `country_currency_desc` value exactly (e.g. `Canada-Dollar`, `Mexico-Peso`, `Japan-Yen`, `Euro Zone-Euro`).

**Rate selection rules**

- The most recent rate whose `effective_date` is ≤ the transaction's `transactionDate` is used
- That rate's `effective_date` must fall within 6 months prior to the `transactionDate`
- Returns `422 Unprocessable Entity` if no qualifying rate exists in the window

See [java-app.md](java-app.md#treasury-rate-selection) for the reasoning behind `effective_date` over `record_date`.

**Response — 200 OK**

```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "description": "Office chair",
  "transactionDate": "2025-03-15",
  "purchaseAmountUsd": 249.99,
  "countryCurrencyDesc": "Canada-Dollar",
  "exchangeRateUsed": 1.355000,
  "exchangeRateDate": "2024-12-31",
  "exchangeRateAgeDays": 74,
  "convertedAmount": 338.74
}
```

---

### GET /actuator/health

Spring Boot Actuator health check. Returns component status (DB connectivity, disk space, etc.).

```json
{ "status": "UP" }
```

---

### GET /version

Build metadata and git information.

```json
{
  "version": "1.0.0",
  "gitCommit": "a1b2c3d4...",
  "gitCommitShort": "a1b2c3d",
  "gitBranch": "main",
  "buildTime": "2025-05-14T23:00:00Z"
}
```

---

## Error Responses

All errors follow a consistent envelope:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "No exchange rate available for Canada-Dollar within 6 months of 2020-01-01"
}
```

| HTTP Status | When |
|-------------|------|
| `400 Bad Request` | Validation failure on request body (missing field, value out of range, etc.) |
| `404 Not Found` | Transaction ID does not exist |
| `422 Unprocessable Entity` | Transaction exists but no qualifying exchange rate found in the 6-month window |
| `500 Internal Server Error` | Unexpected error (Treasury API unreachable, etc.) |

---

## External Dependency: Treasury Fiscal Data API

The service calls the [Treasury Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/) dataset via:

```
GET https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange
    ?fields=country_currency_desc,exchange_rate,effective_date,record_date
    &filter=country_currency_desc:eq:<currency>,effective_date:lte:<purchaseDate>,effective_date:gte:<purchaseDate-6m>
    &sort=-effective_date
    &page[size]=1
```

This is a **read-only, unauthenticated public API**. The service caches results to avoid redundant calls — see [java-app.md](java-app.md#caching) for the full caching strategy.

---

## Testing

| Layer | Tool | Location |
|-------|------|----------|
| Unit / integration (JVM) | JUnit 5, Mockito | `api/currencyconverter/src/test/` |
| API integration (Python) | Python `unittest` + `urllib` | `api/currencyconverter/integration_tests/` |
| Performance / load | Locust | `api/currencyconverter/performance_tests/` |

How to run each layer (Gradle, Docker `tests` service, `start.sh`, Locust UI) is in **[testing.md](testing.md)**.
