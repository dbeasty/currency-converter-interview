"""HTTP integration tests for the currency converter API.

Stdlib only: json, unittest, urllib (via integration_tests.http_client).

Set INTEGRATION_BASE_URL (default http://localhost:8080). Start the app with ./gradlew bootRun first.
"""

from __future__ import annotations

import unittest

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


if __name__ == "__main__":
    unittest.main()
