"""HTTP integration tests for the currency converter API.

Stdlib only: json, unittest, urllib (via integration_tests.http_client).

Set INTEGRATION_BASE_URL (default http://localhost:8080). Start the app with ./gradlew bootRun first.
"""

from __future__ import annotations

import unittest
from datetime import date

try:
    from .http_client import request_json
except ImportError:
    from http_client import request_json


class TestTransactionApi(unittest.TestCase):
    def test_create_transaction_201(self) -> None:
        code, data = request_json(
            "POST",
            "/transactions",
            body={
                "description": "Python integration test",
                "transactionDate": "2024-06-15",
                "purchaseAmountUsd": 42.5,
            },
        )
        self.assertEqual(code, 201, msg=str(data))
        self.assertIsInstance(data, dict)
        assert isinstance(data, dict)
        self.assertIn("id", data)
        self.assertEqual(data.get("description"), "Python integration test")
        self.assertEqual(data.get("transactionDate"), "2024-06-15")
        self.assertEqual(data.get("purchaseAmountUsd"), 42.5)

    def test_get_converted_200(self) -> None:
        code, created = request_json(
            "POST",
            "/transactions",
            body={
                "description": "Convert me",
                "transactionDate": "2024-06-15",
                "purchaseAmountUsd": 100.0,
            },
        )
        self.assertEqual(code, 201, msg=str(created))
        self.assertIsInstance(created, dict)
        assert isinstance(created, dict)
        tx_id = created["id"]

        code2, conv = request_json(
            "GET",
            f"/transactions/{tx_id}",
            query={"countryCurrencyDesc": "Canada-Dollar"},
        )
        self.assertEqual(code2, 200, msg=str(conv))
        self.assertIsInstance(conv, dict)
        assert isinstance(conv, dict)
        for key in (
            "id",
            "description",
            "transactionDate",
            "purchaseAmountUsd",
            "countryCurrencyDesc",
            "exchangeRateUsed",
            "convertedAmount",
        ):
            self.assertIn(key, conv, msg=f"missing {key}: {conv}")
        self.assertEqual(conv.get("countryCurrencyDesc"), "Canada-Dollar")

    def test_get_transaction_not_found_404(self) -> None:
        code, data = request_json(
            "GET",
            "/transactions/00000000-0000-4000-8000-000000000099",
            query={"countryCurrencyDesc": "Canada-Dollar"},
        )
        self.assertEqual(code, 404)
        self.assertIsInstance(data, dict)
        assert isinstance(data, dict)
        self.assertIn("message", data)

    def test_get_monthly_report_200(self) -> None:
        for desc, tx_date, amount in (
            ("Mar A", "2019-03-10", 50.0),
            ("Mar B", "2019-03-20", 25.5),
            ("Apr only", "2019-04-01", 100.0),
        ):
            code, _ = request_json(
                "POST",
                "/transactions",
                body={
                    "description": desc,
                    "transactionDate": tx_date,
                    "purchaseAmountUsd": amount,
                },
            )
            self.assertEqual(code, 201)

        code, report = request_json(
            "GET",
            "/monthly-report",
            query={"month": "03-2019"},
        )
        self.assertEqual(code, 200, msg=str(report))
        self.assertIsInstance(report, dict)
        assert isinstance(report, dict)
        self.assertEqual(report.get("month"), "03-2019")
        self.assertEqual(report.get("transactionCount"), 2)
        self.assertEqual(report.get("totalPurchaseAmountUsd"), 75.5)

    def test_get_monthly_report_invalid_format_400(self) -> None:
        code, data = request_json(
            "GET",
            "/monthly-report",
            query={"month": "2024-06"},
        )
        self.assertEqual(code, 400)
        self.assertIsInstance(data, dict)
        assert isinstance(data, dict)
        self.assertEqual(data.get("message"), "month must be MM-YYYY")

    def test_get_monthly_report_future_month_400(self) -> None:
        today = date.today()
        if today.month == 12:
            future_month = f"01-{today.year + 1}"
        else:
            future_month = f"{today.month + 1:02d}-{today.year}"

        code, data = request_json(
            "GET",
            "/monthly-report",
            query={"month": future_month},
        )
        self.assertEqual(code, 400)
        self.assertIsInstance(data, dict)
        assert isinstance(data, dict)
        self.assertEqual(data.get("message"), "month cannot be in the future")


if __name__ == "__main__":
    unittest.main()
