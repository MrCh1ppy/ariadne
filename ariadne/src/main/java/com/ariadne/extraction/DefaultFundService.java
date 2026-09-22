package com.ariadne.extraction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultFundService implements FundService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final FundSource source;
    private final FundRepository fundRepository;
    private final FundPersistence persistence;
    private final FundWriter writer;
    private final CheckedRangeCache cache;
    private final Clock clock;
    private final LocalDate minimumStartDate;
    private final ConcurrentHashMap<String, Object> fundLocks = new ConcurrentHashMap<>();

    @Autowired
    public DefaultFundService(
            FundSource source,
            FundRepository fundRepository,
            FundPersistence persistence,
            FundWriter writer,
            CheckedRangeCache cache,
            @Value("${ariadne.minimum-start-date}") LocalDate minimumStartDate
    ) {
        this(source, fundRepository, persistence, writer, cache, Clock.system(SHANGHAI), minimumStartDate);
    }

    DefaultFundService(FundSource source, FundRepository fundRepository, FundPersistence persistence, FundWriter writer, CheckedRangeCache cache, Clock clock, LocalDate minimumStartDate) {
        this.source = source;
        this.fundRepository = fundRepository;
        this.persistence = persistence;
        this.writer = writer;
        this.cache = cache;
        this.clock = clock;
        this.minimumStartDate = minimumStartDate;
    }

    @Override
    public List<FundNav> getHistory(String fundCode, String startDate, String endDate) {
        var request = validate(fundCode, startDate, endDate);
        synchronized (fundLocks.computeIfAbsent(fundCode, ignored -> new Object())) {
            for (DateRange missing : cache.missing(fundCode, request, Instant.now(clock))) fetchAndStore(fundCode, missing);
            return persistence.findNavs(fundCode, request.startDate(), request.endDate());
        }
    }

    @Override
    public void refreshFunds() {
        var funds = source.getFunds();
        if (funds == null) throw new UpstreamException("invalid fund catalogue response");
        var seen = new HashSet<String>();
        for (RemoteFund fund : funds) {
            validateFund(fund);
            if (!seen.add(fund.fundCode())) throw new UpstreamException("duplicate fund in catalogue");
        }
        writer.saveFunds(funds.stream().map(DefaultFundService::toFund).toList());
    }

    @Override
    public List<FundNav> refreshHistory(String fundCode, String startDate, String endDate) {
        var request = validate(fundCode, startDate, endDate);
        synchronized (fundLocks.computeIfAbsent(fundCode, ignored -> new Object())) {
            fetchAndStore(fundCode, request);
            return persistence.findNavs(fundCode, request.startDate(), request.endDate());
        }
    }

    private void fetchAndStore(String fundCode, DateRange request) {
        ensureFund(fundCode);
        var remote = source.getHistory(fundCode, request.startDate(), request.endDate());
        var navs = validateNavs(fundCode, request, remote);
        storeNavs(navs);
        cache.markChecked(fundCode, request, LocalDate.now(clock.withZone(SHANGHAI)), Instant.now(clock));
    }

    private void ensureFund(String fundCode) {
        if (fundRepository.existsById(fundCode)) return;
        refreshFunds();
        if (!fundRepository.existsById(fundCode)) throw new FundNotFoundException("fund was not found in the upstream catalogue");
    }

    void storeNavs(List<FundNav> navs) { writer.saveNavs(navs); }

    private List<FundNav> validateNavs(String fundCode, DateRange request, List<RemoteFundNav> remote) {
        if (remote == null) throw new UpstreamException("invalid NAV response");
        var seen = new HashSet<LocalDate>();
        return remote.stream().map(item -> {
            if (item == null) throw new UpstreamException("invalid NAV response");
            if (!fundCode.equals(item.fundCode())) throw new UpstreamException("NAV fund code mismatch");
            var date = parseDate(item.navDate(), "NAV date");
            if (date.isBefore(request.startDate()) || date.isAfter(request.endDate()) || !seen.add(date)) throw new UpstreamException("invalid NAV range or duplicate date");
            return new FundNav(new FundNavId(fundCode, date), parseNav(item.unitNav()));
        }).toList();
    }

    private DateRange validate(String fundCode, String startDate, String endDate) {
        if (fundCode == null || !fundCode.matches("\\d{6}")) throw new BadRequestException("fundCode must be six digits");
        var start = parseDate(startDate, "startDate");
        var end = parseDate(endDate, "endDate");
        if (start.isBefore(minimumStartDate) || end.isAfter(LocalDate.now(clock.withZone(SHANGHAI))) || start.isAfter(end)) throw new BadRequestException("date range is outside allowed bounds");
        return new DateRange(start, end);
    }

    private static LocalDate parseDate(String value, String name) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new BadRequestException(name + " must be YYYY-MM-DD");
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new BadRequestException(name + " must be a real date"); }
    }

    private static BigDecimal parseNav(String value) {
        try {
            if (value == null || !value.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")) throw new NumberFormatException();
            var nav = new BigDecimal(value);
            if (nav.signum() <= 0 || nav.scale() > 10 || nav.precision() - nav.scale() > 10) throw new NumberFormatException();
            return nav;
        } catch (NumberFormatException exception) { throw new UpstreamException("invalid NAV value"); }
    }

    private static void validateFund(RemoteFund fund) {
        if (fund == null || fund.fundCode() == null || !fund.fundCode().matches("\\d{6}") || fund.fundName() == null || fund.fundName().isBlank()) throw new UpstreamException("invalid fund catalogue response");
    }

    private static Fund toFund(RemoteFund fund) { return new Fund(fund.fundCode(), fund.fundName(), fund.fundType(), fund.pinyinAbbreviation(), fund.pinyinFullName()); }
}
