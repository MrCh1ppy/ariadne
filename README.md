# Ariadne

This repository contains two modules:

- `ariadne-akshare`: a synchronous FastAPI adapter around AKShare. It has no database access.
- `ariadne`: a Java 25 / Spring Boot 4 HTTP service with `presentation` and `extraction` packages. Python remains the upstream adapter; the former TypeScript description is obsolete.

The contract is daily fund NAV history. `fundCode` is exactly six digits, dates are real `YYYY-MM-DD` calendar dates, and `unitNav` is a positive decimal string.

## Python Adapter

```bash
cd ariadne-akshare
python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
python -m pytest
python -m mypy src/ariadne_akshare
uvicorn --app-dir src ariadne_akshare.app:app --port 8000
```

The adapter exposes `GET /funds` and `GET /funds/{fundCode}/navs?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`. A fake provider is sufficient for deterministic offline end-to-end checks; real AKShare network access is not required by the tests.

## Java Service

```bash
cd ariadne
export JDBC_URL='jdbc:postgresql://127.0.0.1:5432/ariadne'
export DB_USERNAME=ariadne
export DB_PASSWORD='***'
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew bootRun
```

The service exposes:

- `GET /funds/{fundCode}/navs?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
- `POST /funds/refresh`
- `POST /funds/{fundCode}/navs/refresh?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

Requests are restricted to real dates from `2025-01-01` through today's `Asia/Shanghai` date. Uncovered inclusive ranges are fetched and persisted before being marked checked. Recent and historic ranges have separate TTLs, but this is an in-memory optimization rather than a complete cache guarantee: restart, eviction, or concurrent processes can fetch again.

The migration creates only `fund` and `fund_nav`; NAV identity is the existing `(fund_code, nav_date)` key. `JdbcClient` uses named parameters and `BigDecimal` preserves the PostgreSQL `numeric(20,10)` value.

## Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test
```

Tests are offline by default. `PostgresPersistenceIntegrationTest` is enabled only with `ARIADNE_INTEGRATION_DB=true` and `ARIADNE_TEST_SCHEMA=ariadne_it_<random>`. It uses a disposable schema, creates only local test tables, and drops them afterward. Do not point it at the existing public tables.
