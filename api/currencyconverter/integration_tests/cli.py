#!/usr/bin/env python3
"""Manual CLI for the currency converter API (stdlib: argparse, json, sys).

Uses integration_tests.http_client. Set INTEGRATION_BASE_URL (default http://localhost:8080).
Start the app with ./gradlew bootRun first.

Examples:
  python3 -m integration_tests.cli create --description "Coffee" --date 2024-06-15 --amount 50
  python3 -m integration_tests.cli convert --id <uuid> --currency "Canada-Dollar"
  python3 -m integration_tests.cli smoke
"""

from __future__ import annotations

import argparse
import json
import sys

try:
    from integration_tests.http_client import request_json
except ImportError:
    from http_client import request_json


def _print_body(code: int, data: object, *, json_only: bool) -> None:
    if json_only:
        print(json.dumps(data, separators=(",", ":")))
    else:
        print(f"HTTP {code}")
        print(json.dumps(data, indent=2, default=str))


def _cmd_create(args: argparse.Namespace) -> int:
    body = {
        "description": args.description,
        "transactionDate": args.date,
        "purchaseAmountUsd": float(args.amount),
    }
    code, data = request_json("POST", "/transactions", body=body)
    _print_body(code, data, json_only=args.json_only)
    if not args.json_only and isinstance(data, dict) and "id" in data:
        print(f"\nTransaction id: {data['id']}", file=sys.stderr)
    return 0 if code == 201 else 1


def _cmd_convert(args: argparse.Namespace) -> int:
    code, data = request_json(
        "GET",
        f"/transactions/{args.id}",
        query={"countryCurrencyDesc": args.currency},
    )
    _print_body(code, data, json_only=args.json_only)
    return 0 if code == 200 else 1


def _cmd_smoke(args: argparse.Namespace) -> int:
    create_body = {
        "description": args.description,
        "transactionDate": args.date,
        "purchaseAmountUsd": float(args.amount),
    }
    code1, created = request_json("POST", "/transactions", body=create_body)
    if not args.json_only:
        print("=== POST /transactions ===")
    _print_body(code1, created, json_only=args.json_only)
    if code1 != 201 or not isinstance(created, dict) or "id" not in created:
        return 1
    tx_id = created["id"]
    code2, conv = request_json(
        "GET",
        f"/transactions/{tx_id}",
        query={"countryCurrencyDesc": args.currency},
    )
    if not args.json_only:
        print("\n=== GET /transactions/{id} (converted) ===")
    _print_body(code2, conv, json_only=args.json_only)
    return 0 if code2 == 200 else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Manual HTTP client for currency converter API")
    sub = parser.add_subparsers(dest="command", required=True)

    p_create = sub.add_parser("create", help="POST /transactions")
    p_create.add_argument("--description", required=True)
    p_create.add_argument("--date", required=True, metavar="YYYY-MM-DD", help="transactionDate")
    p_create.add_argument("--amount", required=True, type=float, help="purchaseAmountUsd")
    p_create.add_argument(
        "--json-only",
        action="store_true",
        help="Print compact JSON only (no HTTP line, no id hint)",
    )
    p_create.set_defaults(func=_cmd_create)

    p_conv = sub.add_parser("convert", help="GET /transactions/{id} with countryCurrencyDesc")
    p_conv.add_argument("--id", required=True, metavar="UUID", help="Transaction id")
    p_conv.add_argument(
        "--currency",
        required=True,
        help="Treasury country_currency_desc (e.g. Canada-Dollar)",
    )
    p_conv.add_argument("--json-only", action="store_true")
    p_conv.set_defaults(func=_cmd_convert)

    p_smoke = sub.add_parser("smoke", help="Create a transaction then convert (defaults for quick manual check)")
    p_smoke.add_argument("--description", default="CLI smoke")
    p_smoke.add_argument("--date", default="2024-06-15", metavar="YYYY-MM-DD")
    p_smoke.add_argument("--amount", default=100.0, type=float)
    p_smoke.add_argument("--currency", default="Canada-Dollar")
    p_smoke.add_argument("--json-only", action="store_true")
    p_smoke.set_defaults(func=_cmd_smoke)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
