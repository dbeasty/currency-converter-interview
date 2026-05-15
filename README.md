# currency-converter-interview

A Spring Boot currency conversion service backed by PostgreSQL, with a Docker Compose stack for local development.

## Documentation

- [Docker Setup](docs/docker.md) — running the full stack locally with Docker Compose

## Quick Start

```bash
# 1. Build the JAR
cd api/currencyconverter && ./gradlew bootJar && cd ../..

# 2. Start the stack
docker compose up --build
```

The API will be available at `http://localhost:8080`.
