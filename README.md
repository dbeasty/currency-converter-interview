# Currency Converter

A Spring Boot service that stores USD purchase transactions and converts them to foreign currencies using live rates from the U.S. Treasury Fiscal Data API.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System overview, component diagram, request flow, technology choices |
| [Data Design](docs/data-design.md) | Database schema, tables, relationships, indexes, migration history |
| [Service Design](docs/service-design.md) | API endpoints, request/response contracts, validation rules, error handling |
| [Java Application](docs/java-app.md) | Package structure, layer responsibilities, caching, date handling, configuration |
| [Docker Setup](docs/docker.md) | Running the stack locally, Vault, credentials |
| [Testing](docs/testing.md) | JVM, Python integration, Locust; Docker `tests` service; `start.sh --test` / `--perf` |

---

## Quick Start

Make sure Docker Desktop is running, then from the repo root:

```bash
# Full stack — builds the JAR, starts vault + db + api
./scripts/start.sh

# Database only
./scripts/start.sh --db-only

# Run tests (see docs/testing.md)
./scripts/start.sh --test
./scripts/start.sh --perf

# Detached (background)
./scripts/start.sh -d
```

The API will be available at `http://localhost:8080`.
