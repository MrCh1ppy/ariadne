# Ariadne

Spring Boot 4 / Java 25 service for locally cached fund NAV history. It uses PostgreSQL and obtains uncached data from the Python adapter.

## Run

Set `JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then run:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew bootRun
```

`PYTHON_BASE_URL` defaults to `http://127.0.0.1:8000`. The adapter must provide `GET /funds` and `GET /funds/{fundCode}/navs?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`.

## API

* `GET /funds/{fundCode}/navs?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
* `POST /funds/refresh`
* `POST /funds/{fundCode}/navs/refresh?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

The accepted date range is 2025-01-01 through today's date in `Asia/Shanghai` (override the lower bound with `ARIADNE_MINIMUM_START_DATE`). Uncovered ranges are fetched, persisted, then cached only after success.
