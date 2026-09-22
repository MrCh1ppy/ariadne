import pandas as pd
from fastapi.testclient import TestClient

from ariadne_akshare.app import app, provider


def test_invalid_request_is_422_and_upstream_failure_is_502(monkeypatch):
    client = TestClient(app)
    assert client.get("/funds/abc/navs", params={"startDate": "2024-01-01", "endDate": "2024-01-02"}).status_code == 422
    assert client.get("/funds/022485/navs", params={"startDate": "2024-02-30", "endDate": "2024-03-01"}).status_code == 422
    monkeypatch.setattr(provider, "history", lambda _code: pd.DataFrame([["2024-01-01", 1]], columns=["bad", "columns"]))
    assert client.get("/funds/022485/navs", params={"startDate": "2024-01-01", "endDate": "2024-01-02"}).status_code == 502
