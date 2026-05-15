"""Shared stdlib HTTP helpers for integration_tests (tests + manual CLI)."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request


def base_url() -> str:
    return os.environ.get("INTEGRATION_BASE_URL", "http://localhost:8080").rstrip("/")


def request_json(
    method: str,
    path: str,
    *,
    query: dict[str, str] | None = None,
    body: dict | None = None,
    timeout: int = 60,
) -> tuple[int, dict | list | str | None]:
    url = base_url() + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None
    headers: dict[str, str] = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            code = resp.status
            if not raw:
                return code, None
            return code, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            parsed: dict | list | str | None = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed
