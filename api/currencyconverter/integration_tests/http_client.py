"""Shared stdlib HTTP helpers for integration_tests (tests + manual CLI)."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request

# ── Auth ──────────────────────────────────────────────────────────────────────
# Default client credentials match application.yml app.security.clients[0].
# Override via env vars when testing against different configs.
_DEFAULT_CLIENT_ID     = os.environ.get("INTEGRATION_CLIENT_ID",     "default-client")
_DEFAULT_CLIENT_SECRET = os.environ.get("INTEGRATION_CLIENT_SECRET", "change-me-secret")

# Module-level token cache so we only call /auth/token once per process.
_cached_token: str | None = None


def base_url() -> str:
    return os.environ.get("INTEGRATION_BASE_URL", "http://localhost:8080").rstrip("/")


def get_token(*, client_id: str | None = None, client_secret: str | None = None) -> str:
    """
    Exchanges clientId + clientSecret for a JWT via POST /auth/token.
    Result is cached for the process lifetime.
    """
    global _cached_token
    if _cached_token is not None:
        return _cached_token

    code, data = request_json(
        "POST",
        "/auth/token",
        body={
            "clientId":     client_id     or _DEFAULT_CLIENT_ID,
            "clientSecret": client_secret or _DEFAULT_CLIENT_SECRET,
        },
        _skip_auth=True,
    )
    if code != 200 or not isinstance(data, dict) or "accessToken" not in data:
        raise RuntimeError(f"Failed to obtain auth token: HTTP {code} — {data}")
    _cached_token = data["accessToken"]
    return _cached_token


def request_json(
    method: str,
    path: str,
    *,
    query: dict[str, str] | None = None,
    body: dict | None = None,
    timeout: int = 60,
    _skip_auth: bool = False,
) -> tuple[int, dict | list | str | None]:
    url = base_url() + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None
    headers: dict[str, str] = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if not _skip_auth:
        headers["Authorization"] = "Bearer " + get_token()
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
