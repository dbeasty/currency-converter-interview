"""
Locust performance test suite for the currency converter API.

Scenarios
---------
StoreOnlyUser
    Only POSTs new transactions. Measures raw write throughput and latency of
    transaction creation and Postgres inserts.

ReadOnlyUser
    Seeds one transaction per worker on start-up, then hammers GET conversions
    against a fixed set of currencies. Exercises the 3-tier cache (in-process →
    DB → Treasury API) under concurrent read load.

MixedUser (default)
    Realistic 80/20 split: 80 % GETs on previously created transactions,
    20 % POSTs of new ones. Mimics production traffic shape.

Usage
-----
Headless (CI / scripted):
    locust -f locustfile.py --headless \
        -u 20 -r 5 --run-time 60s \
        --host http://localhost:8080 \
        --html results/report.html \
        --csv results/stats

Interactive dashboard (browser at http://localhost:8089):
    locust -f locustfile.py --host http://localhost:8080

Override host via env var:
    PERF_BASE_URL=http://staging:8080 locust -f locustfile.py --headless ...

See README.md for full instructions.
"""

from __future__ import annotations

import os
import random
import string
import threading
from datetime import date, timedelta

from locust import HttpUser, between, events, tag, task

# ── Auth ──────────────────────────────────────────────────────────────────────
PERF_CLIENT_ID     = os.environ.get("PERF_CLIENT_ID",     "default-client")
PERF_CLIENT_SECRET = os.environ.get("PERF_CLIENT_SECRET", "change-me-secret")

# ── Constants ────────────────────────────────────────────────────────────────

# Currencies sampled from the Treasury Reporting Rates dataset.
CURRENCIES = [
    "Canada-Dollar",
    "Euro Zone-Euro",
    "Japan-Yen",
    "United Kingdom-Pound",
    "Australia-Dollar",
    "Switzerland-Franc",
    "Mexico-Peso",
    "Sweden-Krona",
    "Norway-Krone",
    "Denmark-Krone",
]

# Purchase dates spread across the past ~5 months so the 6-month window always
# has a matching rate without going out of range.
_TODAY = date.today()
_PURCHASE_DATES = [
    (_TODAY - timedelta(days=d)).isoformat()
    for d in range(30, 150, 15)  # 30, 45, 60 … 135 days ago
]

# Shared pool of (transaction_id, purchase_date) pairs accumulated by all
# workers so read-heavy users can convert existing transactions immediately.
_tx_pool: list[tuple[str, str]] = []
_tx_pool_lock = threading.Lock()

BASE_URL = os.environ.get("PERF_BASE_URL", "").rstrip("/")


# ── Helpers ───────────────────────────────────────────────────────────────────

def _random_description(max_len: int = 50) -> str:
    prefix = "perf-"
    suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
    return (prefix + suffix)[:max_len]


def _random_amount() -> float:
    return round(random.uniform(1.00, 9_999.99), 2)


def _random_purchase_date() -> str:
    return random.choice(_PURCHASE_DATES)


def _random_currency() -> str:
    return random.choice(CURRENCIES)


def _create_transaction(client: HttpUser) -> tuple[str, str] | None:
    """POST /transactions and return (id, transactionDate) or None on failure."""
    tx_date = _random_purchase_date()
    payload = {
        "description": _random_description(),
        "transactionDate": tx_date,
        "purchaseAmountUsd": _random_amount(),
    }
    with client.client.post(
        "/transactions",
        json=payload,
        name="POST /transactions",
        catch_response=True,
    ) as resp:
        if resp.status_code == 201:
            data = resp.json()
            return data["id"], data["transactionDate"]
        resp.failure(f"Expected 201, got {resp.status_code}: {resp.text[:200]}")
        return None


def _convert_transaction(client: HttpUser, tx_id: str, currency: str) -> None:
    """GET /transactions/{id}?countryCurrencyDesc=... and validate the response."""
    with client.client.get(
        f"/transactions/{tx_id}",
        params={"countryCurrencyDesc": currency},
        name="GET /transactions/{id} (convert)",
        catch_response=True,
    ) as resp:
        if resp.status_code == 200:
            body = resp.json()
            required = {
                "id", "description", "transactionDate",
                "purchaseAmountUsd", "exchangeRateUsed", "convertedAmount",
            }
            missing = required - body.keys()
            if missing:
                resp.failure(f"Response missing fields: {missing}")
        elif resp.status_code == 422:
            # No rate available for this currency/date window — not a bug,
            # mark as success so it doesn't inflate the failure rate.
            resp.success()
        else:
            resp.failure(f"Unexpected {resp.status_code}: {resp.text[:200]}")


# ── Wait-time shared across user classes ─────────────────────────────────────

_THINK_TIME = between(0.1, 0.5)


# ── Base user with JWT auth ───────────────────────────────────────────────────

class AuthenticatedUser(HttpUser):
    """Base class that obtains a JWT on start-up and attaches it to all requests."""

    abstract = True

    def on_start(self) -> None:
        resp = self.client.post(
            "/auth/token",
            json={"clientId": PERF_CLIENT_ID, "clientSecret": PERF_CLIENT_SECRET},
            name="POST /auth/token",
        )
        if resp.status_code == 200:
            token = resp.json().get("accessToken", "")
            self.client.headers.update({"Authorization": f"Bearer {token}"})
        else:
            raise RuntimeError(f"Auth failed: {resp.status_code} {resp.text}")


# ── User classes ─────────────────────────────────────────────────────────────

class StoreOnlyUser(AuthenticatedUser):
    """Writes new transactions as fast as possible. No reads."""

    wait_time = _THINK_TIME
    weight = 0  # excluded from default mixed run; use --tags store to activate

    @tag("store")
    @task
    def create_transaction(self) -> None:
        result = _create_transaction(self)
        if result:
            tx_id, tx_date = result
            with _tx_pool_lock:
                _tx_pool.append((tx_id, tx_date))


class ReadOnlyUser(AuthenticatedUser):
    """Converts pre-existing transactions. Seeds one transaction on start-up."""

    wait_time = _THINK_TIME
    weight = 0  # excluded from default mixed run; use --tags read to activate

    _seeded_tx: tuple[str, str] | None = None

    def on_start(self) -> None:
        super().on_start()
        result = _create_transaction(self)
        if result:
            self._seeded_tx = result
            with _tx_pool_lock:
                _tx_pool.append(result)

    @tag("read")
    @task
    def convert_transaction(self) -> None:
        with _tx_pool_lock:
            pool_snapshot = list(_tx_pool)

        if not pool_snapshot:
            # Pool not yet populated; create one on the fly.
            result = _create_transaction(self)
            if result:
                with _tx_pool_lock:
                    _tx_pool.append(result)
            return

        tx_id, _ = random.choice(pool_snapshot)
        _convert_transaction(self, tx_id, _random_currency())


class MixedUser(AuthenticatedUser):
    """
    Default user: 80 % reads (convert), 20 % writes (create).

    This is the only class that runs when you start Locust without --tags.
    """

    wait_time = _THINK_TIME
    weight = 1

    def on_start(self) -> None:
        """Obtain JWT then seed the shared pool with one transaction."""
        super().on_start()
        result = _create_transaction(self)
        if result:
            with _tx_pool_lock:
                _tx_pool.append(result)

    @task(4)
    def convert_existing(self) -> None:
        with _tx_pool_lock:
            pool_snapshot = list(_tx_pool)

        if not pool_snapshot:
            self.create_new()
            return

        tx_id, _ = random.choice(pool_snapshot)
        _convert_transaction(self, tx_id, _random_currency())

    @task(1)
    def create_new(self) -> None:
        result = _create_transaction(self)
        if result:
            with _tx_pool_lock:
                _tx_pool.append(result)


# ── Test lifecycle hooks ──────────────────────────────────────────────────────

@events.test_start.add_listener
def on_test_start(environment, **kwargs):  # noqa: ANN001
    print("\n[PERF] Test starting — target:", environment.host or BASE_URL)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):  # noqa: ANN001
    stats = environment.stats
    total = stats.total
    print(
        f"\n[PERF] Test complete — "
        f"requests={total.num_requests} "
        f"failures={total.num_failures} "
        f"rps={total.current_rps:.1f} "
        f"p50={total.get_response_time_percentile(0.50):.0f}ms "
        f"p95={total.get_response_time_percentile(0.95):.0f}ms "
        f"p99={total.get_response_time_percentile(0.99):.0f}ms"
    )
    with _tx_pool_lock:
        print(f"[PERF] Shared transaction pool size: {len(_tx_pool)}")
