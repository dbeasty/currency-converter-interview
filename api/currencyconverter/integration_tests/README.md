# Python integration tests (stdlib only)

Project-wide testing (Docker, `start.sh`, Locust): **[docs/testing.md](../../../docs/testing.md)**.

These tests call the running Spring Boot app over HTTP. No third-party packages are required (`urllib` + `json` + `unittest`).

Shared HTTP helpers live in [`http_client.py`](http_client.py) and are reused by the **manual CLI** ([`cli.py`](cli.py)).

## Prerequisites

1. Start the API from the `currencyconverter` module directory:

   ```bash
   ./gradlew bootRun
   ```

2. Default base URL is `http://localhost:8080`. Override if needed:

   ```bash
   export INTEGRATION_BASE_URL=http://localhost:8080
   ```

## Run automated tests

From `api/currencyconverter`:

```bash
python3 -m unittest integration_tests.test_api -v
```

You can also run the test file directly (uses the same `http_client` module):

```bash
python3 integration_tests/test_api.py -v
```

## Manual CLI

From `api/currencyconverter`, with the app running:

**Create a transaction**

Use a recent `transactionDate` (ISO `YYYY-MM-DD`). Example:

```bash
python3 -m integration_tests.cli create --description "Coffee" --date 2026-04-15 --amount 50
```

Or:

```bash
python3 integration_tests/cli.py create --description "Coffee" --date 2026-04-15 --amount 50
```

Copy the returned `id`, then **convert**:

```bash
python3 -m integration_tests.cli convert --id YOUR-UUID-HERE --currency "Canada-Dollar"
```

**One-shot smoke** (default description/amount, **default date = 15th of the previous calendar month**, then convert to Canada-Dollar; hits the real Treasury API):

```bash
python3 -m integration_tests.cli smoke
```

Optional flags for `smoke`: `--description`, `--date`, `--amount`, `--currency`.

**Monthly purchase report** (`month` is `MM-YYYY`; aggregates by `transactionDate`, no Treasury calls):

```bash
python3 -m integration_tests.cli report --month 04-2026
```

Create a few transactions in that month first (via `create`) if you need non-zero totals.

**Report smoke** (seeds two transactions in the **previous calendar month** and one in the month after, then fetches that report month; no Treasury):

```bash
python3 -m integration_tests.cli report-smoke
```

From the `integration_tests` directory you can also run `python3 cli.py report-smoke` (same module).

**Machine-readable JSON only** (no HTTP banner, no stderr id hint): add `--json-only` to any subcommand.

## Notes

- `test_get_converted_200` and `smoke` / `convert` call the **real** U.S. Treasury Fiscal Data API. They need network access from the JVM and may fail if Treasury is unreachable.
- `test_create_transaction_201`, `test_get_transaction_not_found_404`, and `test_get_monthly_report_*` only exercise your app (no Treasury).
- `test_get_monthly_report_invalid_format_400` uses month `2024-06` (wrong shape: `YYYY-MM`) to assert validation.
- Automated tests and CLI defaults derive sample `transactionDate` values from [`recent_dates.py`](recent_dates.py) (previous calendar month) so fixtures stay current without editing years by hand.
