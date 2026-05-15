#!/bin/sh
# Load POSTGRES_* from Vault KV (REST) when VAULT_ADDR is set; otherwise use env (e.g. docker-compose.db.yml).
set -eu

load_credentials_from_vault() {
  : "${VAULT_ADDR:?VAULT_ADDR is required to load credentials from Vault}"
  : "${VAULT_TOKEN:?VAULT_TOKEN is required to load credentials from Vault}"

  echo "[postgres-entrypoint] Reading secret/currency-converter from ${VAULT_ADDR}"
  payload="$(curl -sf \
    -H "X-Vault-Token: ${VAULT_TOKEN}" \
    "${VAULT_ADDR}/v1/secret/data/currency-converter")" || {
    echo "[postgres-entrypoint] ERROR: Vault REST read failed" >&2
    exit 1
  }

  export POSTGRES_USER
  export POSTGRES_PASSWORD
  export POSTGRES_DB

  POSTGRES_USER="$(echo "$payload" | jq -r '.data.data["spring.datasource.username"] // empty')"
  POSTGRES_PASSWORD="$(echo "$payload" | jq -r '.data.data["spring.datasource.password"] // empty')"
  POSTGRES_DB="$(echo "$payload" | jq -r '.data.data["postgres.db"] // empty')"

  if [ -z "$POSTGRES_DB" ]; then
    jdbc_url="$(echo "$payload" | jq -r '.data.data["spring.datasource.url"] // empty')"
    case "$jdbc_url" in
      jdbc:postgresql://*/*) POSTGRES_DB="${jdbc_url##*/}" ;;
    esac
  fi

  if [ -z "$POSTGRES_USER" ] || [ -z "$POSTGRES_PASSWORD" ] || [ -z "$POSTGRES_DB" ]; then
    echo "[postgres-entrypoint] ERROR: missing postgres.db, spring.datasource.username, or spring.datasource.password in Vault" >&2
    exit 1
  fi

  echo "[postgres-entrypoint] Using database=${POSTGRES_DB} user=${POSTGRES_USER}"
}

if [ -n "${VAULT_ADDR:-}" ] && [ -n "${VAULT_TOKEN:-}" ]; then
  load_credentials_from_vault
elif [ -z "${POSTGRES_USER:-}" ] || [ -z "${POSTGRES_PASSWORD:-}" ] || [ -z "${POSTGRES_DB:-}" ]; then
  echo "[postgres-entrypoint] ERROR: set VAULT_ADDR+VAULT_TOKEN or POSTGRES_DB/USER/PASSWORD" >&2
  exit 1
fi

exec /usr/local/bin/docker-entrypoint.sh "$@"
