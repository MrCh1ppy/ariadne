from __future__ import annotations

from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Protocol

import pandas as pd
from fastapi import HTTPException


class NavProvider(Protocol):
    def history(self, fund_code: str) -> pd.DataFrame: ...
    def funds(self) -> pd.DataFrame: ...
    def trade_dates(self) -> pd.DataFrame: ...


class AkshareProvider:
    def funds(self) -> pd.DataFrame:
        try:
            import akshare as ak
            return ak.fund_name_em()
        except Exception as exc:
            raise HTTPException(502, "upstream request error") from exc

    def history(self, fund_code: str) -> pd.DataFrame:
        try:
            import akshare as ak
            return ak.fund_open_fund_info_em(symbol=fund_code, indicator="单位净值走势")
        except Exception as exc:
            raise HTTPException(502, "upstream request error") from exc

    def trade_dates(self) -> pd.DataFrame:
        try:
            import akshare as ak
            return ak.tool_trade_date_hist_sina()
        except Exception as exc:
            raise HTTPException(502, "upstream request error") from exc


def _decimal(value: object) -> str:
    try:
        missing = value is None or bool(pd.isna(value))
    except (TypeError, ValueError):
        missing = True
    if missing:
        raise HTTPException(502, "upstream data error")
    try:
        number = Decimal(str(value))
    except (InvalidOperation, ValueError):
        raise HTTPException(502, "upstream data error") from None
    if not number.is_finite() or number <= 0:
        raise HTTPException(502, "upstream data error")
    return format(number, "f")


def read_history(provider: NavProvider, fund_code: str, start: date, end: date) -> list[dict[str, str]]:
    try:
        frame = provider.history(fund_code)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "upstream request error") from exc
    if not {"净值日期", "单位净值"}.issubset(frame.columns):
        raise HTTPException(502, "upstream data error")
    seen: dict[date, str] = {}
    for raw_date, raw_nav in frame[["净值日期", "单位净值"]].itertuples(index=False, name=None):
        try:
            nav_date = date.fromisoformat(str(raw_date))
        except ValueError:
            raise HTTPException(502, "upstream data error") from None
        nav = _decimal(raw_nav)
        previous = seen.get(nav_date)
        if previous is not None and Decimal(previous) != Decimal(nav):
            raise HTTPException(502, "upstream data error")
        seen[nav_date] = nav
    return [
        {"fundCode": fund_code, "navDate": day.isoformat(), "unitNav": seen[day]}
        for day in sorted(seen)
        if start <= day <= end
    ]


def read_trade_dates(provider: NavProvider, start: date, end: date) -> dict[str, object]:
    try:
        frame = provider.trade_dates()
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "upstream request error") from exc
    if "trade_date" not in frame.columns:
        raise HTTPException(502, "upstream data error")
    dates: set[date] = set()
    for raw_date in frame["trade_date"].tolist():
        try:
            if raw_date is None or bool(pd.isna(raw_date)):
                raise ValueError
            if isinstance(raw_date, pd.Timestamp):
                parsed = raw_date.date()
            elif isinstance(raw_date, date):
                parsed = raw_date
            elif isinstance(raw_date, str):
                parsed = date.fromisoformat(raw_date)
            else:
                raise ValueError
        except (TypeError, ValueError):
            raise HTTPException(502, "upstream data error") from None
        dates.add(parsed)
    if not dates:
        raise HTTPException(502, "upstream data error")
    ordered = sorted(dates)
    if start < ordered[0] or end > ordered[-1]:
        raise HTTPException(502, "trade date coverage unavailable")
    return {
        "coverageStart": ordered[0],
        "coverageEnd": ordered[-1],
        "tradeDates": [day for day in ordered if start <= day <= end],
    }


def read_funds(provider: NavProvider) -> list[dict[str, str | None]]:
    try:
        frame = provider.funds()
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "upstream request error") from exc
    columns = {"基金代码", "基金简称", "基金类型", "拼音缩写", "拼音全称"}
    if not columns.issubset(frame.columns):
        raise HTTPException(502, "upstream data error")
    seen: dict[str, dict[str, str | None]] = {}
    for code, name, kind, abbreviation, full_name in frame[["基金代码", "基金简称", "基金类型", "拼音缩写", "拼音全称"]].itertuples(index=False, name=None):
        fund_code, fund_name = str(code), str(name)
        record = {"fundCode": fund_code, "fundName": fund_name, "fundType": _optional(kind), "pinyinAbbreviation": _optional(abbreviation), "pinyinFullName": _optional(full_name)}
        if len(fund_code) != 6 or not fund_code.isdigit() or not fund_name.strip():
            raise HTTPException(502, "upstream data error")
        if fund_code in seen and seen[fund_code] != record:
            raise HTTPException(502, "upstream data error")
        seen[fund_code] = record
    return [seen[code] for code in sorted(seen)]


def _optional(value: object) -> str | None:
    try:
        if value is None or bool(pd.isna(value)):
            return None
    except (TypeError, ValueError):
        return None
    return str(value)
