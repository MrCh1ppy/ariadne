package com.ariadne.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ariadne.extraction.FundNav;
import com.ariadne.extraction.FundNavId;
import com.ariadne.extraction.BadRequestException;
import com.ariadne.extraction.FundService;
import com.ariadne.extraction.FundSource;
import com.ariadne.extraction.RemoteTradeCalendar;
import com.ariadne.extraction.UpstreamException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundAnalysisServiceTest {
    private static final LocalDate MINIMUM = LocalDate.of(2025, 1, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void calculatesSlidingThirtyTradingDayMeans() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(31);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory("022485", MINIMUM.plusDays(0).toString(), dates.getLast().toString())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", MINIMUM.plusDays(29).toString(), dates.getLast().toString());

        assertEquals(List.of(
                new AnalysisPoint(dates.get(29), "30", "15.5000000000"),
                new AnalysisPoint(dates.get(30), "31", "16.5000000000")
        ), result.points());
    }

    @Test
    void skipsWeekendsBecauseCalendarDefinesTheWindow() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = new ArrayList<LocalDate>();
        for (var day = MINIMUM; dates.size() < 31; day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) dates.add(day);
        }
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory("022485", dates.get(0).toString(), dates.getLast().toString())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", dates.get(29).toString(), dates.getLast().toString());

        assertEquals(2, result.points().size());
        verify(funds).getHistory("022485", dates.get(0).toString(), dates.getLast().toString());
    }

    @Test
    void propagatesMissingNavUntilTheWindowRecovers() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(31);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        var navs = navs(dates);
        navs.remove(0);
        when(funds.getHistory(any(), any(), any())).thenReturn(navs);

        var result = service(funds, source).analyze("022485", dates.get(29).toString(), dates.getLast().toString());

        assertEquals(null, result.points().get(0).ma30());
        assertEquals("16.5000000000", result.points().get(1).ma30());
        assertEquals(List.of("MA30 unavailable for 1 point(s)"), result.warnings());
    }

    @Test
    void reportsUnpublishedCurrentNav() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(31);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        var navs = navs(dates);
        navs.removeLast();
        when(funds.getHistory(any(), any(), any())).thenReturn(navs);

        var result = service(funds, source).analyze("022485", dates.get(29).toString(), dates.getLast().toString());

        assertEquals(null, result.points().getLast().unitNav());
        assertEquals(List.of("NAV missing for 1 trading day(s)", "MA30 unavailable for 1 point(s)"), result.warnings());
    }

    @Test
    void rejectsWhenThirtyDayWindowPrecedesMinimum() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(10);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));

        assertThrows(BadRequestException.class, () -> service(funds, source).analyze("022485", MINIMUM.toString(), dates.getLast().toString()));
        verify(funds, never()).getHistory(any(), any(), any());
    }

    @Test
    void returnsNoPointsForCoveredHolidayRange() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        when(source.getTradeDates(MINIMUM, MINIMUM.plusDays(1))).thenReturn(new RemoteTradeCalendar(
                MINIMUM, MINIMUM.plusDays(1), List.of(MINIMUM)));

        var result = service(funds, source).analyze("022485", MINIMUM.plusDays(1).toString(), MINIMUM.plusDays(1).toString());

        assertEquals(List.of(), result.points());
        verify(funds, never()).getHistory(any(), any(), any());
    }

    @Test
    void rejectsInvalidCalendarAndPropagatesSourceFailure() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        when(source.getTradeDates(MINIMUM, MINIMUM.plusDays(30))).thenReturn(new RemoteTradeCalendar(
                MINIMUM, MINIMUM.plusDays(30), List.of(MINIMUM.plusDays(2), MINIMUM.plusDays(1))));
        assertThrows(UpstreamException.class, () -> service(funds, source).analyze("022485", MINIMUM.toString(), MINIMUM.plusDays(30).toString()));

        when(source.getTradeDates(MINIMUM, MINIMUM.plusDays(30))).thenThrow(new UpstreamException("offline"));
        assertThrows(UpstreamException.class, () -> service(funds, source).analyze("022485", MINIMUM.toString(), MINIMUM.plusDays(30).toString()));
    }

    private static FundAnalysisService service(FundService funds, FundSource source) {
        return new FundAnalysisService(funds, source, CLOCK, MINIMUM);
    }

    private static RemoteTradeCalendar calendar(List<LocalDate> dates) {
        return new RemoteTradeCalendar(MINIMUM, dates.getLast(), dates);
    }

    private static List<LocalDate> days(int count) {
        var dates = new ArrayList<LocalDate>();
        for (var i = 0; i < count; i++) dates.add(MINIMUM.plusDays(i));
        return dates;
    }

    private static List<FundNav> navs(List<LocalDate> dates) {
        var result = new ArrayList<FundNav>();
        for (var i = 0; i < dates.size(); i++) {
            result.add(new FundNav(new FundNavId("022485", dates.get(i)), BigDecimal.valueOf(i + 1L)));
        }
        return result;
    }
}
