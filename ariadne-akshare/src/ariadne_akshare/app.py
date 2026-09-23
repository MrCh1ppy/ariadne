from datetime import date
from typing import Annotated
from decimal import Decimal

from fastapi import FastAPI, HTTPException, Path, Query
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from .service import AkshareProvider, read_funds, read_history, read_trade_dates

app = FastAPI(title="Ariadne AKShare adapter")
provider = AkshareProvider()


Code = Annotated[str, Path(alias="fundCode", pattern=r"^\d{6}$")]
Day = Annotated[str, Query(pattern=r"^\d{4}-\d{2}-\d{2}$")]


def parse_day(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise HTTPException(422, "date must be a real YYYY-MM-DD calendar date") from exc


class Nav(BaseModel):
    model_config = ConfigDict(extra="forbid")
    fundCode: str = Field(pattern=r"^\d{6}$")
    navDate: date
    unitNav: str = Field(pattern=r"^(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)$")

    @field_validator("unitNav")
    @classmethod
    def positive(cls, value: str) -> str:
        number = Decimal(value)
        if not number.is_finite() or number <= 0:
            raise ValueError("unitNav must be positive")
        return value


class Range(BaseModel):
    startDate: date
    endDate: date

    @model_validator(mode="after")
    def ordered(self) -> "Range":
        if self.startDate > self.endDate:
            raise ValueError("startDate must be no later than endDate")
        return self


class Fund(BaseModel):
    model_config = ConfigDict(extra="forbid")
    fundCode: str = Field(pattern=r"^\d{6}$")
    fundName: str = Field(min_length=1)
    fundType: str | None
    pinyinAbbreviation: str | None
    pinyinFullName: str | None


class TradeDates(BaseModel):
    coverageStart: date
    coverageEnd: date
    tradeDates: list[date]


@app.get("/funds/{fundCode}/navs", response_model=list[Nav])
def navs(
    fund_code: Code,
    startDate: Day,
    endDate: Day,
) -> list[Nav]:
    dates = Range(startDate=parse_day(startDate), endDate=parse_day(endDate))
    return [Nav.model_validate(item) for item in read_history(provider, fund_code, dates.startDate, dates.endDate)]


@app.get("/funds", response_model=list[Fund])
def funds() -> list[Fund]:
    return [Fund.model_validate(item) for item in read_funds(provider)]


@app.get("/trade-dates", response_model=TradeDates)
def trade_dates(startDate: Day, endDate: Day) -> TradeDates:
    start, end = parse_day(startDate), parse_day(endDate)
    if start > end:
        raise HTTPException(422, "startDate must be no later than endDate")
    dates = Range(startDate=start, endDate=end)
    return TradeDates.model_validate(read_trade_dates(provider, dates.startDate, dates.endDate))
