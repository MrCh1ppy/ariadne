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
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FundAnalysisServiceTest {
    private static final LocalDate MINIMUM = LocalDate.of(2025, 1, 1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private static AnalysisPoint point(LocalDate date, String nav, String ma30, String pct30, String ma60, String pct60) {
        var map = new EnumMap<MaPeriod, MaValue>(MaPeriod.class);
        map.put(MaPeriod.MA30, new MaValue(ma30, pct30));
        map.put(MaPeriod.MA60, new MaValue(ma60, pct60));
        return new AnalysisPoint(date, nav, map);
    }

    @Test
    void calculatesSlidingThirtyAndSixtyTradingDayMeansWithOneFetch() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(61);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory("022485", MINIMUM.plusDays(0).toString(), dates.getLast().toString())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", dates.get(59).toString(), dates.getLast().toString());

        assertEquals(List.of(
                point(dates.get(59), "60", "45.5000000000", "31.87", "30.5000000000", "96.72"),
                point(dates.get(60), "61", "46.5000000000", "31.18", "31.5000000000", "93.65")
        ), result.points());
        verify(funds).getHistory("022485", MINIMUM.toString(), dates.getLast().toString());
    }

    @Test
    void skipsWeekendsBecauseCalendarDefinesTheWindow() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = new ArrayList<LocalDate>();
        for (var day = MINIMUM; dates.size() < 61; day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) dates.add(day);
        }
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory("022485", dates.get(0).toString(), dates.getLast().toString())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", dates.get(59).toString(), dates.getLast().toString());

        assertEquals(2, result.points().size());
        verify(funds).getHistory("022485", dates.get(0).toString(), dates.getLast().toString());
    }

    @Test
    void propagatesMissingNavUntilTheWindowRecovers() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(61);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        var navs = navs(dates);
        navs.remove(0);
        when(funds.getHistory(any(), any(), any())).thenReturn(navs);

        var result = service(funds, source).analyze("022485", dates.get(59).toString(), dates.getLast().toString());

        assertEquals("45.5000000000", result.points().get(0).movingAverages().get(MaPeriod.MA30).value());
        assertEquals("31.87", result.points().get(0).movingAverages().get(MaPeriod.MA30).deviationPercent());
        assertEquals(null, result.points().get(0).movingAverages().get(MaPeriod.MA60).value());
        assertEquals(null, result.points().get(0).movingAverages().get(MaPeriod.MA60).deviationPercent());
        assertEquals("31.5000000000", result.points().get(1).movingAverages().get(MaPeriod.MA60).value());
        assertEquals("93.65", result.points().get(1).movingAverages().get(MaPeriod.MA60).deviationPercent());
        assertEquals(List.of("MA60 unavailable for 1 point(s)"), result.warnings());
    }

    @Test
    void eachWindowRecoversWhenTheMissingTradingDayExitsIt() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(91);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        var history = navs(dates);
        history.remove(30);
        when(funds.getHistory(any(), any(), any())).thenReturn(history);

        var points = service(funds, source).analyze("022485", dates.get(59).toString(), dates.getLast().toString()).points();
        assertEquals(null, points.get(0).movingAverages().get(MaPeriod.MA30).value());
        assertEquals(null, points.get(0).movingAverages().get(MaPeriod.MA60).value());
        assertEquals("46.5000000000", points.get(1).movingAverages().get(MaPeriod.MA30).value());
        assertEquals(null, points.get(1).movingAverages().get(MaPeriod.MA60).value());
        assertEquals(null, points.get(30).movingAverages().get(MaPeriod.MA60).value());
        assertEquals("61.5000000000", points.get(31).movingAverages().get(MaPeriod.MA60).value());
        assertEquals("47.97", points.get(31).movingAverages().get(MaPeriod.MA60).deviationPercent());
    }

    @Test
    void reportsUnpublishedCurrentNav() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(61);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        var navs = navs(dates);
        navs.removeLast();
        when(funds.getHistory(any(), any(), any())).thenReturn(navs);

        var result = service(funds, source).analyze("022485", dates.get(59).toString(), dates.getLast().toString());

        assertEquals(null, result.points().getLast().unitNav());
        assertEquals(null, result.points().getLast().movingAverages().get(MaPeriod.MA30).deviationPercent());
        assertEquals(null, result.points().getLast().movingAverages().get(MaPeriod.MA60).value());
        assertEquals(null, result.points().getLast().movingAverages().get(MaPeriod.MA60).deviationPercent());
        assertEquals(List.of("NAV missing for 1 trading day(s)", "MA60 unavailable for 1 point(s)",
                "MA30 unavailable for 1 point(s)"), result.warnings());
    }

    @Test
    void computesSignedPercentFromRoundedAverageAndHandlesMissingOrNonPositiveAverage() {
        assertEquals("10.00", FundAnalysisService.navVsMaPercent(new BigDecimal("1.10"), new BigDecimal("1.0000000000")));
        assertEquals("-10.00", FundAnalysisService.navVsMaPercent(new BigDecimal("0.90"), new BigDecimal("1.0000000000")));
        assertEquals("0.00", FundAnalysisService.navVsMaPercent(BigDecimal.ONE, new BigDecimal("1.0000000000")));
        assertEquals(null, FundAnalysisService.navVsMaPercent(null, BigDecimal.ONE));
        assertEquals(null, FundAnalysisService.navVsMaPercent(BigDecimal.ONE, null));
        assertEquals(null, FundAnalysisService.navVsMaPercent(BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(null, FundAnalysisService.navVsMaPercent(BigDecimal.ONE, BigDecimal.ONE.negate()));
    }

    @Test
    void rejectsWhenSixtyDayWindowPrecedesMinimum() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(59);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));

        assertThrows(BadRequestException.class, () -> service(funds, source).analyze("022485", MINIMUM.toString(), dates.getLast().toString()));
        verify(funds, never()).getHistory(any(), any(), any());
    }

    @Test
    void rejectsThirtyDaysOfHistoryWhenSixtyAreRequired() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(45);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));

        assertThrows(BadRequestException.class, () -> service(funds, source).analyze("022485", dates.get(29).toString(), dates.getLast().toString()));
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

    @Test
    void selectsOnlyRequestedPeriods() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(30);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory(any(), any(), any())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", dates.get(29).toString(), dates.getLast().toString(), Set.of(MaPeriod.MA30));

        assertEquals(1, result.points().size());
        assertEquals(Set.of(MaPeriod.MA30), result.points().get(0).movingAverages().keySet());
        assertEquals("15.5000000000", result.points().get(0).movingAverages().get(MaPeriod.MA30).value());
        assertEquals("93.55", result.points().get(0).movingAverages().get(MaPeriod.MA30).deviationPercent());
    }

    @Test
    void supportsOneHundredTwentyTradingDayWindow() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(120);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));
        when(funds.getHistory(any(), any(), any())).thenReturn(navs(dates));

        var result = service(funds, source).analyze("022485", dates.get(119).toString(), dates.getLast().toString(),
                Set.of(MaPeriod.MA120));

        assertEquals(1, result.points().size());
        assertEquals("60.5000000000", result.points().get(0).movingAverages().get(MaPeriod.MA120).value());
        assertEquals("98.35", result.points().get(0).movingAverages().get(MaPeriod.MA120).deviationPercent());
        verify(funds).getHistory("022485", MINIMUM.toString(), dates.getLast().toString());
    }

    @Test
    void rejectsOneHundredTwentyDayWindowWhenHistoryIsShorter() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        var dates = days(119);
        when(source.getTradeDates(MINIMUM, dates.getLast())).thenReturn(calendar(dates));

        assertThrows(BadRequestException.class,
                () -> service(funds, source).analyze("022485", dates.get(118).toString(), dates.getLast().toString(), Set.of(MaPeriod.MA120)));
        verify(funds, never()).getHistory(any(), any(), any());
    }

    @Test
    void rejectsDuplicateAndInvalidPeriodNames() {
        var source = mock(FundSource.class);
        var funds = mock(FundService.class);
        assertThrows(BadRequestException.class,
                () -> service(funds, source).analyze("022485", "2026-01-01", "2026-01-01", Set.of()));
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
