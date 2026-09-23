from datetime import date

import pandas as pd
import pytest
from fastapi import HTTPException

from ariadne_akshare.service import read_funds, read_history, read_trade_dates


class Fake:
    def __init__(self, frame): self.frame = frame
    def history(self, fund_code: str): return self.frame
    def funds(self): return self.frame
    def trade_dates(self): return self.frame


def test_filter_sort_and_dedupe():
    frame = pd.DataFrame([["2024-01-03", 1.2, 0], ["2024-01-01", 1.1, 0], ["2024-01-03", 1.2, 0]], columns=["净值日期", "单位净值", "增长"])
    assert read_history(Fake(frame), "022485", pd.Timestamp("2024-01-02").date(), pd.Timestamp("2024-01-03").date()) == [{"fundCode": "022485", "navDate": "2024-01-03", "unitNav": "1.2"}]


@pytest.mark.parametrize("value", [float("nan"), float("inf"), -1])
def test_bad_nav(value):
    with pytest.raises(HTTPException):
        read_history(Fake(pd.DataFrame([["2024-01-01", value]], columns=["净值日期", "单位净值"])), "022485", pd.Timestamp("2024-01-01").date(), pd.Timestamp("2024-01-01").date())


def test_empty_is_valid():
    assert read_history(Fake(pd.DataFrame([["2024-01-01", 1]], columns=["净值日期", "单位净值"])), "022485", pd.Timestamp("2025-01-01").date(), pd.Timestamp("2025-01-02").date()) == []


def test_trade_dates_sort_dedupe_and_filter():
    frame = pd.DataFrame({"trade_date": [date(2024, 1, 3), date(2024, 1, 1), date(2024, 1, 3), date(2024, 1, 2)]})
    assert read_trade_dates(Fake(frame), date(2024, 1, 2), date(2024, 1, 3)) == {
        "coverageStart": date(2024, 1, 1),
        "coverageEnd": date(2024, 1, 3),
        "tradeDates": [date(2024, 1, 2), date(2024, 1, 3)],
    }


def test_trade_dates_holiday_is_empty_but_covered():
    result = read_trade_dates(Fake(pd.DataFrame({"trade_date": [date(2024, 1, 2), date(2024, 1, 4)]})), date(2024, 1, 3), date(2024, 1, 3))
    assert result["tradeDates"] == []


@pytest.mark.parametrize("frame", [
    pd.DataFrame({"wrong": [date(2024, 1, 1)]}),
    pd.DataFrame({"trade_date": [None]}),
    pd.DataFrame({"trade_date": ["not-a-date"]}),
])
def test_trade_dates_reject_bad_upstream_values(frame):
    with pytest.raises(HTTPException) as error:
        read_trade_dates(Fake(frame), date(2024, 1, 1), date(2024, 1, 1))
    assert error.value.status_code == 502


def test_trade_dates_reject_coverage_gap():
    with pytest.raises(HTTPException, match="coverage"):
        read_trade_dates(Fake(pd.DataFrame({"trade_date": [date(2024, 1, 2), date(2024, 1, 4)]})), date(2023, 12, 31), date(2024, 1, 3))


def test_trade_dates_propagates_source_failure():
    class Broken(Fake):
        def trade_dates(self): raise RuntimeError("offline")

    with pytest.raises(HTTPException, match="upstream request"):
        read_trade_dates(Broken(pd.DataFrame()), date(2024, 1, 1), date(2024, 1, 1))


def test_funds_preserve_all_five_fields_and_reject_conflicting_duplicates():
    frame = pd.DataFrame([["022485", "Fund", None, None, "fund"], ["022485", "Fund", None, None, "fund"]], columns=["基金代码", "基金简称", "基金类型", "拼音缩写", "拼音全称"])
    assert read_funds(Fake(frame)) == [{"fundCode": "022485", "fundName": "Fund", "fundType": None, "pinyinAbbreviation": None, "pinyinFullName": "fund"}]
    frame.loc[1, "基金简称"] = "Other"
    with pytest.raises(HTTPException): read_funds(Fake(frame))
