"""Rolling calendar-month helpers for integration fixtures (stdlib only).

Keeps sample transactionDate values in the recent past (previous calendar month)
instead of hard-coded years.
"""

from __future__ import annotations

from calendar import monthrange
from datetime import date


def _prev_year_month(ref: date | None = None) -> tuple[int, int]:
    d = ref or date.today()
    if d.month == 1:
        return d.year - 1, 12
    return d.year, d.month - 1


def _next_year_month(year: int, month: int) -> tuple[int, int]:
    if month == 12:
        return year + 1, 1
    return year, month + 1


def format_ymd(year: int, month: int, day: int) -> str:
    last = monthrange(year, month)[1]
    return f"{year:04d}-{month:02d}-{min(day, last):02d}"


def last_month_mid_date(ref: date | None = None) -> str:
    """ISO date on the 15th of the previous calendar month."""
    y, m = _prev_year_month(ref)
    return format_ymd(y, m, 15)


def monthly_report_fixture_bundle(ref: date | None = None) -> dict[str, str]:
    """Two transactions in the previous month, one in the following month.

    GET /monthly-report for ``report_month_mm_yyyy`` should include only the
    first two rows (same behavior as the old March/April 2019 fixture).
    """
    y, m = _prev_year_month(ref)
    ny, nm = _next_year_month(y, m)
    return {
        "tx_a": format_ymd(y, m, 10),
        "tx_b": format_ymd(y, m, 20),
        "tx_other_month": format_ymd(ny, nm, 1),
        "report_month_mm_yyyy": f"{m:02d}-{y}",
    }


def parse_mm_yyyy(s: str) -> tuple[int, int] | None:
    """Parse ``MM-YYYY`` into ``(year, month)`` or return None."""
    parts = s.split("-", 1)
    if len(parts) != 2:
        return None
    try:
        month_part, year_part = int(parts[0]), int(parts[1])
    except ValueError:
        return None
    if not (1 <= month_part <= 12 and 1 <= year_part <= 9999):
        return None
    return year_part, month_part


def transactions_for_report_month(mm_yyyy: str) -> tuple[str, str, str] | None:
    """(tx_a, tx_b, tx_other) ISO dates for monthly-report smoke, or None if invalid."""
    parsed = parse_mm_yyyy(mm_yyyy)
    if parsed is None:
        return None
    y, m = parsed
    ny, nm = _next_year_month(y, m)
    return (
        format_ymd(y, m, 10),
        format_ymd(y, m, 20),
        format_ymd(ny, nm, 1),
    )
