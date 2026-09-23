package com.ariadne.extraction;

import java.time.LocalDate;
import java.util.List;

public record RemoteTradeCalendar(
        LocalDate coverageStart,
        LocalDate coverageEnd,
        List<LocalDate> tradeDates
) {}
