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
* `GET /funds/{fundCode}/analysis?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD[&periods=MA5,MA15,MA30,MA60,MA120]`
* `POST /funds/refresh`
* `POST /funds/{fundCode}/navs/refresh?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

The accepted date range is 2025-01-01 through today's date in `Asia/Shanghai` (override the lower bound with `ARIADNE_MINIMUM_START_DATE`). Uncovered ranges are fetched, persisted, then cached only after success.

`/funds/search` accepts a three-to-six-digit code prefix, returns at most 20 funds in code order, and lazily refreshes the catalogue only when the local fund directory is empty. The legacy `suffix` query parameter is accepted for cached older frontend bundles but still uses prefix matching; new clients should send `prefix`. If both are supplied, they must be identical. Results may be truncated for short prefixes; continue typing to narrow them (for example, `prefix=022485` selects `022485`). Matching is against the start of the code, so `prefix=022` matches `022485`, not `002248`.

Analysis points contain only trading days, ordered by date. Each point has `date`, `unitNav` (decimal string or null), and `movingAverages`, a map keyed by `MA5`, `MA15`, `MA30`, `MA60`, and `MA120`. Each average has `value` (decimal string or null) and `deviationPercent` (decimal string without `%`, or null); there are no flat MA fields. By default all five periods are returned. Optional `periods` selects one or more distinct periods from these five (for example, `periods=MA5,MA15`). The longest selected period determines the required calendar history: the first displayed trading day must have 4, 14, 29, 59, or 119 earlier A-share trading days within `ARIADNE_MINIMUM_START_DATE`, respectively, or the request returns 400. In particular, omitting `periods` still requires MA120 history even for an otherwise valid shorter-period request.

Each MA is the arithmetic mean of NAVs for the current and preceding `period - 1` A-share trading days, independently calculated and rounded to scale 10 with `HALF_UP`. Missing NAVs invalidate only the windows containing that day; they remain null rather than zero or a replacement from an earlier day. `deviationPercent` is `(unitNav / reported MA - 1) * 100`, rounded to two decimal places with `HALF_UP`, or null when NAV/MA is unavailable or MA is nonpositive. These comparisons are not investment returns. The NAV range is fetched once per analysis request, starting at the longest selected window's first trading day; no cross-request MA cache is used.
