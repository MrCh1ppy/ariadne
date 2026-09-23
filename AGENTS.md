# AGENTS.md

Ariadne is a three-module fund NAV repo (no root-level build; each module is independent):

- `ariadne/` — Java 25 / Spring Boot 4 HTTP service. Packages: `presentation` (controllers), `extraction` (fetch + persist + cache), `analysis` (MA30/MA60).
- `ariadne-akshare/` — synchronous FastAPI adapter around AKShare. No database access; it is the Java service's upstream.
- `ariadne-web/` — Vue 3 + TypeScript + Vite frontend. Not copied into Spring Boot static resources.

## Commands (run from the module directory, not the repo root)

Java (`ariadne/`):
```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew test
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew bootRun   # needs JDBC_URL, DB_USERNAME, DB_PASSWORD
```
`JAVA_HOME` must point to a JDK 25 install; the toolchain will not fall back to an older system Java.

Python (`ariadne-akshare/`):
```sh
python -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt
python -m pytest          # offline; monkeypatches the AKShare provider
python -m mypy src/ariadne_akshare   # mypy strict mode
uvicorn --app-dir src ariadne_akshare.app:app --port 8000
```

Web (`ariadne-web/`): `npm run typecheck`, `npm test` (vitest + jsdom), `npm run build`. Dev server proxies `/api/*` to `http://127.0.0.1:18080` (override with `VITE_JAVA_URL`).

## Java service specifics

- Integration test `PostgresPersistenceIntegrationTest` runs only with `ARIADNE_INTEGRATION_DB=true` and a disposable `ARIADNE_TEST_SCHEMA=ariadne_it_<random>`; never point it at the real public tables.
- Schema comes from `src/main/resources/migrations/001_funds.sql` via `spring.sql.init` (no Flyway). Tables: `fund`, `fund_nav` with PK `(fund_code, nav_date)`; `unit_nav` is `numeric(20,10)` — keep `BigDecimal` end to end.
- `PYTHON_BASE_URL` defaults to `http://127.0.0.1:8000` (the adapter must be running). Accepted date range: `ARIADNE_MINIMUM_START_DATE` (default 2025-01-01) through today in `Asia/Shanghai`.
- The recent/historic TTL caches are an in-memory optimization only, not a cache guarantee — restart, eviction, or concurrent processes can re-fetch. Don't write code that assumes cached == persisted truth.
- `/funds/search` uses **prefix** matching on the fund code (`prefix=022` matches `022485`, not `002248`); a legacy `suffix` parameter is still accepted for old frontend bundles and must match `prefix` if both are sent. New clients send `prefix` only.

## Contract

`fundCode` is exactly six digits; dates are real `YYYY-MM-DD` calendar dates; `unitNav` is a positive decimal string. MA30/MA60 windows use A-share trading days fetched 59 days back; scale 10, `HALF_UP`, null when any window value is missing.

## Docs

Root `README.md` covers the cross-module contract; each module has its own README with run/deploy details (`ariadne-web/README.md` documents the ecs-user nginx deployment). Trust executable config (`application.yml`, `build.gradle.kts`, `pyproject.toml`, `package.json`) over prose when they disagree.
