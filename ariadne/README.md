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
* `GET /funds/search?prefix=022`
* `GET /funds/{fundCode}/analysis?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
* `POST /funds/refresh`
* `POST /funds/{fundCode}/navs/refresh?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

The accepted date range is 2025-01-01 through today's date in `Asia/Shanghai` (override the lower bound with `ARIADNE_MINIMUM_START_DATE`). Uncovered ranges are fetched, persisted, then cached only after success.

`/funds/search` accepts a three-to-six-digit code prefix, returns at most 20 funds in code order, and lazily refreshes the catalogue only when the local fund directory is empty. The legacy `suffix` query parameter is accepted for cached older frontend bundles but still uses prefix matching; new clients should send `prefix`. If both are supplied, they must be identical. Results may be truncated for short prefixes; continue typing to narrow them (for example, `prefix=022485` selects `022485`). Matching is against the start of the code, so `prefix=022` matches `022485`, not `002248`. Analysis points contain only trading days. `ma30` is the arithmetic mean of the current and previous 29 A-share trading-day NAVs, rounded to scale 10 with `HALF_UP`; it is null when any value in that window is missing.

Each analysis point includes `navVsMa30Percent`: `(unitNav / ma30 - 1) * 100`, using the reported scale-10 MA30 and rounding the percentage to two decimal places with `HALF_UP`. It is a decimal string (without a `%` sign), or null if either value is missing or MA30 is not positive.

`ma60` averages the current and previous 59 A-share trading days, independently of MA30, with scale 10 and `HALF_UP`. The analysis starts its single NAV fetch 59 trading days before the first displayed day and rejects requests without that much calendar history. Missing NAVs invalidate only windows containing that day. `navVsMa60Percent` follows the same scale-10-average and two-decimal `HALF_UP` percentage rules as MA30; these comparisons are not investment returns.
