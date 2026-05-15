# Currency Converter

A Spring Boot service that stores USD purchase transactions and converts them to foreign currencies using live rates from the U.S. Treasury Fiscal Data API.

---

## Requirements Summary

- Stores purchase transactions with `description`, `transactionDate`, and `purchaseAmountUsd`.
- Validates required fields, enforces description max length (50), and ensures purchase amount is positive and rounded to cents.
- Persists each purchase with a unique identifier.
- Retrieves stored purchases converted to a requested Treasury currency (`country_currency_desc`).
- Selects the most recent Treasury rate with `effective_date <= transactionDate` and within the prior 6 months.
- Returns an error when no qualifying exchange rate exists within that 6-month window.
- Rounds converted purchase amounts to two decimal places.
- Supports production-style usage via embedded app server, Dockerized local stack, and automated tests. See [docs/docker.md](docs/docker.md) and [docs/testing.md](docs/testing.md).

---

## How to run

There are two supported ways to run the application:

| Approach | When to use |
|----------|-------------|
| **Docker (recommended)** | Default for reviewers and day-to-day use. Vault, PostgreSQL, and the API run in containers—no host database install. Start with `./scripts/start.sh` from the repo root. |
| **Standalone on the host** | Run `./gradlew bootRun` in `api/currencyconverter`. Default profile is **`h2`** (in-memory H2, no database install). |

The sections below list **requirements** and **quick start** for each path.

---

## Requirements

| Requirement | Notes |
|-------------|--------|
| **Java 21** | `java -version` |
| **Build** | `api/currencyconverter/gradlew` (or `gradlew.bat` on Windows) |
| **Docker Desktop or equivalent** | To run `docker compose` (Docker path only; see [Quick Start (Docker)](#quick-start-docker)) |

---

## Quick Start (Docker)

1. Install **Docker Desktop** (or equivalent) and ensure it is running.
2. Ensure **Java 21** is available (`java -version`).
3. From the **repository root**:

**macOS / Linux:**

```bash
./scripts/start.sh
```

**Windows (CMD or PowerShell):**

```bat
scripts\start.bat
```

**Detached (background):**

```bash
./scripts/start.sh -d
```

```bat
scripts\start.bat -d
```

The API is at **http://localhost:8080**.

---

## Quick Start (standalone on the host)

From `api/currencyconverter`:

```bash
./gradlew bootRun --args='--spring.profiles.active=h2'
```

The API listens on **http://localhost:8080**.

---

## More Start Options

For other start options reference the [docs/java-app.md](docs/java-app.md) and the [docs/docker.md](docs/docker.md).

---

## Verify (both modes)

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/version
```

Get a JWT and call the API (see [security.md](docs/security.md)):

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"default-client","clientSecret":"change-me-secret"}'
```

For more test and validation details, see [docs/testing.md](docs/testing.md).

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System overview, component diagram, request flow, technology choices |
| [Data Design](docs/data-design.md) | Database schema, tables, relationships, indexes, migration history |
| [Service Design](docs/service-design.md) | API endpoints, request/response contracts, validation rules, error handling |
| [Java Application](docs/java-app.md) | Package structure, profiles `local` / `h2` / `release`, caching, configuration |
| [Docker Setup](docs/docker.md) | Compose services, Vault, credentials, health checks |
| [Security](docs/security.md) | JWT client-credentials auth, HashiCorp Vault, HTTPS requirements |
| [Testing](docs/testing.md) | JVM, Python integration, Locust; Docker `tests` service; `start.sh` / `start.bat` |

---

## AI Assistance

This project was developed using **AI-assisted development** with [Cursor](https://cursor.sh), using planned prompting and technical direction throughout. AI tools assisted with code generation, documentation, architecture design, and test strategy.

All output was reviewed and validated using my own technical expertise, including key design decisions around caching strategy, exchange rate filtering, secrets management, and test coverage.

---

## Usage and Liability Disclaimer

This project is licensed under the [MIT License](LICENSE). You are free to use, copy, modify, and redistribute all or part of this code for personal or commercial purposes.

This project is provided "as is", without warranty of any kind, express or implied. The author is not liable for any claim, damages, or other liability arising from or related to the use of this software.
