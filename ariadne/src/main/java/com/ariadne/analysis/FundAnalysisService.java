package com.ariadne.analysis;

import com.ariadne.extraction.BadRequestException;
import com.ariadne.extraction.FundNav;
import com.ariadne.extraction.FundService;
import com.ariadne.extraction.RemoteTradeCalendar;
import com.ariadne.extraction.FundSource;
import com.ariadne.extraction.UpstreamException;
import com.ariadne.extraction.DateRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FundAnalysisService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal WINDOW_SIZE = BigDecimal.valueOf(30);
    private final FundService fundService;
    private final FundSource source;
    private final Clock clock;
    private final LocalDate minimumStartDate;

    @Autowired
    public FundAnalysisService(
            FundService fundService,
            FundSource source,
            @Value("${ariadne.minimum-start-date}") LocalDate minimumStartDate
    ) {
        this(fundService, source, Clock.system(SHANGHAI), minimumStartDate);
    }

    FundAnalysisService(FundService fundService, FundSource source, Clock clock, LocalDate minimumStartDate) {
        this.fundService = fundService;
        this.source = source;
        this.clock = clock;
        this.minimumStartDate = minimumStartDate;
    }

    public FundAnalysis analyze(String fundCode, String startDate, String endDate) {
        var request = validate(fundCode, startDate, endDate);
        var calendar = source.getTradeDates(minimumStartDate, request.endDate());
        validateCalendar(calendar, request.endDate());
        var allTradeDates = calendar.tradeDates();
        var displayDates = allTradeDates.stream()
                .filter(day -> !day.isBefore(request.startDate()) && !day.isAfter(request.endDate()))
                .toList();
        if (displayDates.isEmpty()) {
            return new FundAnalysis(fundCode, request.startDate(), request.endDate(), List.of(), List.of());
        }

        var firstDisplayIndex = allTradeDates.indexOf(displayDates.getFirst());
        if (firstDisplayIndex < 29) {
            throw new BadRequestException("MA30 calculation window precedes minimum start date");
        }
        var windowStart = allTradeDates.get(firstDisplayIndex - 29);
        var navs = fundService.getHistory(fundCode, windowStart.toString(), request.endDate().toString());
        var values = new HashMap<LocalDate, BigDecimal>();
        for (FundNav nav : navs) values.put(nav.id().navDate(), nav.unitNav());

        var points = new ArrayList<AnalysisPoint>();
        var missingNavDays = 0;
        var unavailableMaDays = 0;
        for (var day : displayDates) {
            var nav = values.get(day);
            if (nav == null) missingNavDays++;
            var index = allTradeDates.indexOf(day);
            var window = allTradeDates.subList(index - 29, index + 1);
            var complete = window.stream().allMatch(values::containsKey);
            if (!complete) unavailableMaDays++;
            points.add(new AnalysisPoint(day, decimal(nav), complete ? mean(window, values) : null));
        }
        var warnings = new ArrayList<String>();
        if (missingNavDays > 0) warnings.add("NAV missing for " + missingNavDays + " trading day(s)");
        if (unavailableMaDays > 0) warnings.add("MA30 unavailable for " + unavailableMaDays + " point(s)");
        return new FundAnalysis(fundCode, request.startDate(), request.endDate(), points, warnings);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String mean(List<LocalDate> window, Map<LocalDate, BigDecimal> values) {
        var sum = window.stream().map(values::get).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(WINDOW_SIZE, 10, RoundingMode.HALF_UP).toPlainString();
    }

    private void validateCalendar(RemoteTradeCalendar calendar, LocalDate endDate) {
        if (calendar == null || calendar.coverageStart() == null || calendar.coverageEnd() == null || calendar.tradeDates() == null
                || calendar.coverageStart().isAfter(calendar.coverageEnd())
                || minimumStartDate.isBefore(calendar.coverageStart()) || endDate.isAfter(calendar.coverageEnd())) {
            throw new UpstreamException("invalid trade calendar coverage");
        }
        var seen = new HashSet<LocalDate>();
        LocalDate previous = null;
        for (var day : calendar.tradeDates()) {
            if (day == null || day.isBefore(minimumStartDate) || day.isAfter(endDate)
                    || (previous != null && !previous.isBefore(day)) || !seen.add(day)) {
                throw new UpstreamException("invalid trade calendar");
            }
            previous = day;
        }
    }

    private DateRange validate(String fundCode, String startDate, String endDate) {
        if (fundCode == null || !fundCode.matches("\\d{6}")) throw new BadRequestException("fundCode must be six digits");
        var start = parseDate(startDate, "startDate");
        var end = parseDate(endDate, "endDate");
        var today = LocalDate.now(clock.withZone(SHANGHAI));
        if (start.isBefore(minimumStartDate) || end.isAfter(today) || start.isAfter(end)) {
            throw new BadRequestException("date range is outside allowed bounds");
        }
        return new DateRange(start, end);
    }

    private static LocalDate parseDate(String value, String name) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new BadRequestException(name + " must be YYYY-MM-DD");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(name + " must be a real date");
        }
    }
}
