# Treasury Fiscal Data Integration

## Overview

The application converts stored purchase amounts to foreign currencies using the
[Treasury Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange)
dataset, accessed via the
[Fiscal Data API](https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange).

---

## Known Limitations and Production Suitability

The Treasury Reporting Rates of Exchange API is a **government fiscal reporting dataset**, not a financial market data feed. This distinction has significant consequences for any system that needs accurate, current exchange rates.

### Publication frequency

Treasury publishes rates on a **quarterly basis** (March 31, June 30, September 30, December 31), with occasional mid-quarter amendments for highly volatile currencies. This means a rate in this dataset can be up to **three months old** for most currencies at any given time.

This is fundamentally unsuitable for day-to-day currency conversion where intraday or even daily swings matter:

- A rate published on March 31 may be used for a purchase made on June 29 — nearly 90 days later.
- For currencies like Argentine Peso or Turkish Lira, which can move 10–30 % in a single month, the applied rate may be significantly different from the actual market rate on the purchase date.

### No intraday or daily rates

The dataset has no concept of intraday rates. Even with mid-quarter amendments, the finest granularity is a single rate per currency per amendment event — not a daily close, bid/ask spread, or real-time tick.

### Ambiguous update timing

There is no guaranteed publish time within a day. The application's cache key uses today's date in Eastern Time to trigger a daily miss, but Treasury does not commit to publishing new data at a specific time. An amendment published at 4 PM ET would not be picked up until the next day's cache miss if today's rate was already cached.

### Scope: intended use case for this API

The Treasury API is designed for **US federal government accounting and reporting purposes** — specifically for agencies converting foreign currency expenditures for budget reporting. It is not designed as a financial exchange rate service.

### What this means for this application

This application satisfies the stated interview requirements precisely — those requirements explicitly reference the Treasury Reporting Rates of Exchange API and its 6-month window rule. The implementation is correct within those constraints.

However, **this should not be used as the basis for a production-quality currency conversion system** in any context where:

- Rate accuracy within days or weeks matters
- High-volatility currencies (Argentina, Turkey, Egypt, Nigeria, etc.) are involved
- Financial or legal obligations depend on the converted amount

For production use, replace `TreasuryApiClient` with a feed from a commercial provider (e.g. Open Exchange Rates, Fixer.io, XE, or a financial data platform) that publishes daily or intraday rates. The three-layer caching architecture and `ExchangeRateService` interface are designed to accommodate this without changes to the rest of the codebase.

---

## Why we filter and sort by `effective_date`, not `record_date`

### What the two date fields mean

| Field | What it represents |
|---|---|
| `record_date` | The fiscal quarter-end date the report was originally filed (e.g. `2025-03-31`) |
| `effective_date` | The date this specific rate row became authoritative |

For stable currencies these two fields are always identical. For volatile currencies — Argentina,
Turkey, Egypt, and others — Treasury publishes **mid-quarter amendments**. An amendment keeps the
original `record_date` (so it still belongs to that quarter) but carries a later `effective_date`
reflecting when the corrected rate became effective.

### Concrete example (Argentina-Peso, observed from the live API)

```
record_date   effective_date  exchange_rate
2025-03-31    2025-04-15      1230.0   ← mid-quarter amendment
2025-03-31    2025-03-31      1093.0   ← original quarterly baseline
```

If a purchase was made on **2025-05-01** and we filter on `record_date:lte:2025-05-01`, both rows
satisfy the condition (both have `record_date 2025-03-31`). Sorting by `-record_date` gives them
equal ordering weight, making the result non-deterministic. More importantly, the **amendment
(1230.0, effective 2025-04-15)** is the rate that was actually in force on the purchase date, while
the baseline (1093.0) had already been superseded.

### The correct approach

Filter and sort by `effective_date`:

```
filter: effective_date:lte:<purchaseDate>, effective_date:gte:<purchaseDate - 6 months>
sort:   -effective_date
size:   1
```

This guarantees:

1. Only rates that were **already in force on or before the purchase date** are considered.
2. If Treasury published an amendment for a given quarter and that amendment's `effective_date` ≤
   purchase date, it surfaces above the baseline — because it has the later (more recent) effective
   date.
3. The 6-month lower bound on `effective_date` satisfies the assignment requirement: *"must use a
   currency conversion rate … from within the last 6 months"*.

`record_date` is still requested in the `fields` parameter and stored in `TreasuryRateRow` for
traceability (it identifies the fiscal quarter), but it plays no role in filtering or sorting.

---

## Caching strategy

### What is cached

Each unique `(countryCurrencyDesc, purchaseDate, asOfDate)` triple maps to a single
`TreasuryRateRow` in an in-process Caffeine/Simple cache named `treasuryRates`.

`asOfDate` is today's date in **Treasury's timezone** (Eastern Time, see below). It is included in
the key so the cached rate is automatically invalidated when the calendar day rolls over in ET —
the same rhythm Treasury uses when publishing new data.

A `null` result (no qualifying rate in the 6-month window) is **not cached** (`unless = "#result == null"`). This prevents a transient data gap from being permanently locked into the cache.

### Sequence for a cache miss

```
GET /transactions/{id}?countryCurrencyDesc=Argentina-Peso
        │
        ▼
ExchangeRateService.findMostRecentRate(currency, purchaseDate)
        │  passes asOfDate = LocalDate.now(ET)
        ▼
TreasuryRateCache.load(currency, purchaseDate, asOfDate)   ← @Cacheable
        │  cache miss
        ▼
TreasuryApiClient.fetchBestRateWithinWindow(currency, purchaseDate, purchaseDate - 6m)
        │  HTTP GET → Fiscal Data API
        ▼
TreasuryRateRow { effectiveDate, exchangeRate, recordDate }
        │  stored in cache; returned to caller
        ▼
CurrencyConversionService  →  upsert ExchangeRate entity  →  save ConversionRecord
```

### Why the cache key includes `asOfDate`

Without `asOfDate` in the key, a rate fetched on Monday would be reused on Tuesday even after
Treasury published an updated rate Monday evening. Including today's date (in ET) causes an
automatic cache miss on each new Treasury publication day.

---

## Timezone handling

### Treasury's timezone (Eastern Time)

Treasury's publication schedule and business calendar operate in **Eastern Time (ET)**. The
`app.treasury.timezone` configuration property (default `America/New_York`) is resolved to a
`ZoneId` and used in `ExchangeRateService` when computing `LocalDate.now(treasuryZone)` for the
cache key. This means:

- Between midnight UTC and midnight ET (up to UTC-4 in summer / UTC-5 in winter), the application
  considers it still "yesterday" in Treasury's world and does not fire an unnecessary extra API
  call.
- Once ET rolls over to a new day, the cache key changes and a fresh API call is made to pick up
  any newly published rates.

### Our internal timestamps (UTC / `Instant`)

All internally generated audit timestamps — `Transaction.createdAt`,
`ExchangeRate.sourceTimestamp`, and `ConversionRecord.conversionTimestamp` — are stored as
`java.time.Instant`. `Instant` is always UTC and maps to a `TIMESTAMP WITH TIME ZONE` column in
PostgreSQL, ensuring no timezone ambiguity in the audit trail.

### Calendar dates (`LocalDate`)

`transaction_date` and `exchange_rate_effective_date` are calendar dates (`LocalDate` / SQL `DATE`
with no time component). They represent a day on the calendar (e.g., *"the purchase happened on
2025-04-01"*) and deliberately carry no timezone information. Interpreting them relative to a
timezone would change their meaning — a purchase on 2025-04-01 in New York is not a different
calendar date depending on where the server runs.

---

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `app.treasury.base-url` | `https://api.fiscaldata.treasury.gov/services/api/fiscal_service` | Base URL for the Fiscal Data API |
| `app.treasury.timezone` | `America/New_York` | IANA timezone used to align cache key dates with Treasury's publication calendar |
