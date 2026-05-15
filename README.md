# currency-converter-interview

A Spring Boot currency conversion service backed by PostgreSQL, with a Docker Compose stack for local development.

## Documentation

- [Docker Setup](docs/docker.md) — running the full stack locally, credentials, and deploy instructions

## Quick Start

Make sure Docker Desktop is running, then from the repo root:

```bash
# Full stack — builds the JAR, starts api + db
./scripts/start.sh

# Database only
./scripts/start.sh --db-only

# Run in the background
./scripts/start.sh -d
```

The API will be available at `http://localhost:8080`.
