from datetime import date

import pandas as pd
from fastapi.testclient import TestClient

from ariadne_akshare.app import app, provider


def test_invalid_request_is_422_and_upstream_failure_is_502(monkeypatch):
    client = TestClient(app)
    assert client.get("/funds/abc/navs", params={"startDate": "2024-01-01", "endDate": "2024-01-02"}).status_code == 422
    assert client.get("/funds/022485/navs", params={"startDate": "2024-02-30", "endDate": "2024-03-01"}).status_code == 422
    monkeypatch.setattr(provider, "history", lambda _code: pd.DataFrame([["2024-01-01", 1]], columns=["bad", "columns"]))
    assert client.get("/funds/022485/navs", params={"startDate": "2024-01-01", "endDate": "2024-01-02"}).status_code == 502


def test_trade_dates_returns_coverage_and_holiday_empty(monkeypatch):
    monkeypatch.setattr(provider, "trade_dates", lambda: pd.DataFrame({"trade_date": [date(2024, 1, 2), date(2024, 1, 4)]}))
    response = TestClient(app).get("/trade-dates", params={"startDate": "2024-01-03", "endDate": "2024-01-03"})
    assert response.status_code == 200
    assert response.json() == {"coverageStart": "2024-01-02", "coverageEnd": "2024-01-04", "tradeDates": []}


def test_trade_dates_rejects_invalid_range_and_coverage(monkeypatch):
    monkeypatch.setattr(provider, "trade_dates", lambda: pd.DataFrame({"trade_date": [date(2024, 1, 2), date(2024, 1, 4)]}))
    client = TestClient(app)
    assert client.get("/trade-dates", params={"startDate": "2024-02-30", "endDate": "2024-03-01"}).status_code == 422
    assert client.get("/trade-dates", params={"startDate": "2024-01-04", "endDate": "2024-01-02"}).status_code == 422
    assert client.get("/trade-dates", params={"startDate": "2024-01-01", "endDate": "2024-01-02"}).status_code == 502
    assert client.get("/trade-dates", params={"startDate": "2024-01-02", "endDate": "2024-01-05"}).status_code == 502


def test_trade_dates_maps_source_failure_to_502(monkeypatch):
    monkeypatch.setattr(provider, "trade_dates", lambda: (_ for _ in ()).throw(RuntimeError("offline")))
    assert TestClient(app).get("/trade-dates", params={"startDate": "2024-01-02", "endDate": "2024-01-02"}).status_code == 502
