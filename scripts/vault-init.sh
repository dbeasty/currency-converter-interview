#!/usr/bin/env sh
set -eu

: "${VAULT_ADDR:?VAULT_ADDR is required}"
: "${VAULT_TOKEN:?VAULT_TOKEN is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

JWT_SECRET="${APP_SECURITY_JWT_SECRET:-change-me-in-production-must-be-at-least-32-chars!!}"

echo "[vault-init] Waiting for Vault at ${VAULT_ADDR}..."
until vault status > /dev/null 2>&1; do
  sleep 1
done

if ! vault secrets list -format=json | grep -q '"secret/"'; then
  echo "[vault-init] Enabling KV v2 at secret/"
  vault secrets enable -path=secret kv-v2
fi

echo "[vault-init] Seeding secret/currency-converter"
vault kv put secret/currency-converter \
  postgres.db="${POSTGRES_DB}" \
  spring.datasource.url="jdbc:postgresql://db:5432/${POSTGRES_DB}" \
  spring.datasource.username="${POSTGRES_USER}" \
  spring.datasource.password="${POSTGRES_PASSWORD}" \
  spring.datasource.driver-class-name="org.postgresql.Driver" \
  spring.jpa.database-platform="org.hibernate.dialect.PostgreSQLDialect" \
  spring.jpa.hibernate.ddl-auto="validate" \
  app.security.jwt-secret="${JWT_SECRET}" \
  app.security.jwt-expiry-seconds="3600" \
  'app.security.clients[0].client-id'="default-client" \
  'app.security.clients[0].client-secret'="change-me-secret" \
  'app.security.clients[1].client-id'="admin-client" \
  'app.security.clients[1].client-secret'="change-me-admin-secret"

echo "[vault-init] Done"
