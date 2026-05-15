# Currency Converter API

A Spring Boot application that stores purchase transactions and retrieves them converted to a
specified country's currency using live rates from the
[Treasury Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange) API.

---

## Prerequisites

- Java 21+
- No database installation required — uses an embedded H2 database by default

---

## Running locally

```bash
./gradlew bootRun
```

The application starts on **http://localhost:8080**.

---

## API Endpoints

### Store a purchase transaction

```
POST /transactions
Content-Type: application/json
```

**Request body**

```json
{
  "description": "Office chair",
  "transactionDate": "2025-03-15",
  "purchaseAmountUsd": 249.99
}
```

**Rules**
- `description` — required, max 50 characters
- `transactionDate` — required, must not be a future date
- `purchaseAmountUsd` — required, positive amount; rounded to nearest cent

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

### Retrieve a transaction converted to a foreign currency

```
GET /transactions/{id}?countryCurrencyDesc=Canada-Dollar
```

The `countryCurrencyDesc` value must match the Treasury API's `country_currency_desc` field
(e.g. `Canada-Dollar`, `Mexico-Peso`, `Japan-Yen`).

**Conversion rules**
- Uses the most recent rate whose `effective_date` is ≤ the transaction date
- Rate must be within the 6 months prior to the transaction date
- Returns 400 if no qualifying rate exists

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

### Health check

```
GET /actuator/health
```

---

### Build info and git version

```
GET /version
```

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

## Docker

To run with a Postgres database using Docker Compose (from the repo root):

```bash
./gradlew bootJar
docker compose up --build
```

See [docs/docker.md](../../docs/docker.md) for details.

---

## Running tests

```bash
./gradlew test
```

Python integration tests (requires the app to be running):

```bash
cd integration_tests
python3 -m unittest test_api -v
```

---

## Key design decisions

- **H2 in-memory** for zero-setup local development; schema and Java types are fully PostgreSQL-compatible via Liquibase migrations
- **`effective_date` filtering** on the Treasury API rather than `record_date` — ensures mid-quarter amendments for volatile currencies (Argentina, Turkey, etc.) surface correctly
- **`Instant`** for all audit timestamps (UTC); `LocalDate` for calendar dates (timezone-free)
- **Caching** of Treasury API responses keyed by currency, purchase date, and today's date in Eastern Time (Treasury's publication timezone) — see [TREASURY_SERVICE.md](TREASURY_SERVICE.md)
