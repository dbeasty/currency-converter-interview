# Security

How the Currency Converter API authenticates callers, stores secrets, and what must be enabled in production.

---

## Authentication model

The API uses **OAuth2-style client credentials** (not end-user login):

1. **POST /auth/token** — client sends `clientId` and `clientSecret` (JSON).
2. Server validates against registered clients in `app.security.clients`.
3. Server returns a short-lived **JWT** (`accessToken`; default TTL 3600 seconds).
4. All other API calls send **`Authorization: Bearer <jwt>`**.

| Component | Role |
|-----------|------|
| `AuthController` | Token issuance |
| `JwtTokenProvider` | HS256 sign and verify |
| `JwtAuthFilter` | Validates Bearer on each request |
| `SecurityConfig` | Stateless sessions, route rules |

### Public endpoints (no JWT)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/token` | Obtain JWT |
| GET | `/actuator/health` | Health probe |
| GET | `/version` | Build and git metadata |

Everything else (for example `POST /transactions`, `GET /transactions/{id}`) requires a valid Bearer token.

### Token request and response

```http
POST /auth/token
Content-Type: application/json

{ "clientId": "default-client", "clientSecret": "change-me-secret" }
```

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

- **200** — token issued  
- **401** — unknown `clientId` or wrong `clientSecret`

### Configuration properties

| Property | Purpose |
|----------|---------|
| `app.security.jwt-secret` | HS256 signing key (**≥ 32 bytes** in production) |
| `app.security.jwt-expiry-seconds` | Token lifetime in seconds (default `3600`) |
| `app.security.clients` | List of `{ client-id, client-secret }` pairs |

Dev defaults are in `application-local.yml`. Override in production via environment variables (relaxed binding), Vault KV, or both — **never commit production secrets to git**.

### Test and load-test clients

Python tools do **not** read Vault; they must use credentials that match what the JVM loads.

| Tool | Env vars | Defaults |
|------|----------|----------|
| Integration tests / CLI | `INTEGRATION_CLIENT_ID`, `INTEGRATION_CLIENT_SECRET` | `default-client` / `change-me-secret` |
| Locust | `PERF_CLIENT_ID`, `PERF_CLIENT_SECRET` | `default-client` / `change-me-secret` |

See [testing.md](testing.md).

---

## HashiCorp Vault

Only the **Spring Boot application** integrates with Vault (Spring Cloud Vault). PostgreSQL does not call Vault; the API (or the DB container entrypoint in Docker) loads JDBC credentials from KV and connects normally.

| Profile | Vault behavior |
|---------|----------------|
| **`local`** (default for `./gradlew bootRun`) | `optional:vault://` in `application-local.yml` — app starts if Vault is unreachable; KV can override datasource and `app.security.*` |
| **`release`** (Docker Compose `api` service) | `vault://` **required** in `application-release.yml` — needs `VAULT_ADDR` and `VAULT_TOKEN`; `fail-fast: true` |

Typical KV v2 path: **`secret/currency-converter`** (`spring.cloud.vault.kv.default-context: application`).

Keys commonly stored there:

- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
- `app.security.jwt-secret`
- `app.security.clients` (structure must match what Spring Boot binding expects)

Docker flow (`vault` → `vault-init` → `db` → `api`) is documented in [docker.md](docker.md).

Example (local Vault CLI):

```bash
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=dev-root-token
vault kv put secret/currency-converter \
  spring.datasource.password='...' \
  app.security.jwt-secret='change-me-in-production-must-be-at-least-32-chars!!'
```

---

## HTTPS (required in production)

The application serves **plain HTTP** on port **8080** by default. **TLS is not configured inside the Spring Boot app.**

In production, terminate **HTTPS in front of** the API, for example:

- Reverse proxy (nginx, Traefik, ALB) with TLS certificates  
- Kubernetes Ingress with TLS  
- Service mesh (optional mTLS between services)

Without HTTPS, `clientSecret` and JWTs travel in cleartext on the network. Client credentials and Bearer tokens must only be sent over **HTTPS** (or an equivalently protected private network).

### Production checklist

- [ ] TLS 1.2+ on the public listener  
- [ ] Redirect HTTP → HTTPS or block plain HTTP  
- [ ] Strong `app.security.jwt-secret` and client secrets in Vault (not in git)  
- [ ] Rotate JWT secret and client secrets on compromise  
- [ ] Restrict Vault KV read access  
- [ ] Consider shorter `app.security.jwt-expiry-seconds` where appropriate  

---

## Other security notes

- **CSRF** is disabled — stateless JWT API; no browser cookie session.  
- **Sessions** are stateless (`SessionCreationPolicy.STATELESS`).  
- **H2 console** (`/h2-console`) is **not** permitted in `SecurityConfig`; profile `h2` is for local dev only.  
- **Health details:** profile `local` uses `management.endpoint.health.show-details: always`; profile `release` uses `when_authorized`.  
- **Actuator:** only `health` is exposed over HTTP (`management.endpoints.web.exposure.include: health`).

---

## Related docs

| Topic | Document |
|-------|----------|
| API contracts (including `/auth/token`) | [service-design.md](service-design.md) |
| Docker, Vault, Compose | [docker.md](docker.md) |
| Profiles and local run | [java-app.md](java-app.md) |
| Integration and Locust auth | [testing.md](testing.md) |
