# Python integration tests (stdlib only)

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

```bash
python3 -m integration_tests.cli create --description "Coffee" --date 2024-06-15 --amount 50
```

Or:

```bash
python3 integration_tests/cli.py create --description "Coffee" --date 2024-06-15 --amount 50
```

Copy the returned `id`, then **convert**:

```bash
python3 -m integration_tests.cli convert --id YOUR-UUID-HERE --currency "Canada-Dollar"
```

**One-shot smoke** (default description/date/amount, then convert to Canada-Dollar; hits the real Treasury API):

```bash
python3 -m integration_tests.cli smoke
```

Optional flags for `smoke`: `--description`, `--date`, `--amount`, `--currency`.

**Machine-readable JSON only** (no HTTP banner, no stderr id hint): add `--json-only` to any subcommand.

## Notes

- `test_get_converted_200` and `smoke` / `convert` call the **real** U.S. Treasury Fiscal Data API. They need network access from the JVM and may fail if Treasury is unreachable.
- `test_create_transaction_201` and `test_get_transaction_not_found_404` only exercise your app and H2.
