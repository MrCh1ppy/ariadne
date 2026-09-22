package com.ariadne.extraction;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CheckedRangeCache {
    private final Cache<String, List<Range>> ranges = Caffeine.newBuilder().maximumSize(20_000).build();
    private final Duration recentTtl;
    private final Duration historicTtl;

    public CheckedRangeCache(
            @org.springframework.beans.factory.annotation.Value("${ariadne.recent-cache-ttl}") Duration recentTtl,
            @org.springframework.beans.factory.annotation.Value("${ariadne.historic-cache-ttl}") Duration historicTtl
    ) {
        this.recentTtl = recentTtl;
        this.historicTtl = historicTtl;
    }

    public List<DateRange> missing(String fundCode, DateRange request, Instant now) {
        var active = active(fundCode, now);
        var missing = new ArrayList<DateRange>();
        LocalDate cursor = request.startDate();
        for (Range checked : active) {
            if (checked.endDate().isBefore(cursor) || checked.startDate().isAfter(request.endDate())) continue;
            if (checked.startDate().isAfter(cursor)) missing.add(new DateRange(cursor, checked.startDate().minusDays(1)));
            if (!checked.endDate().isBefore(cursor)) cursor = checked.endDate().plusDays(1);
            if (cursor.isAfter(request.endDate())) break;
        }
        if (!cursor.isAfter(request.endDate())) missing.add(new DateRange(cursor, request.endDate()));
        return missing;
    }

    public void markChecked(String fundCode, DateRange checked, LocalDate today, Instant now) {
        var updated = active(fundCode, now);
        var expiresAt = now.plus(checked.endDate().equals(today) ? recentTtl : historicTtl);
        updated.add(new Range(checked.startDate(), checked.endDate(), expiresAt));
        // Do not merge different expirations: doing so could make a recent range stale for 24 hours.
        updated.sort(Comparator.comparing(Range::startDate));
        var merged = new ArrayList<Range>();
        for (Range range : updated) {
            if (!merged.isEmpty()) {
                var previous = merged.getLast();
                if (previous.expiresAt().equals(range.expiresAt()) && !range.startDate().isAfter(previous.endDate().plusDays(1))) {
                    merged.set(merged.size() - 1, new Range(previous.startDate(), max(previous.endDate(), range.endDate()), previous.expiresAt()));
                    continue;
                }
            }
            merged.add(range);
        }
        ranges.put(fundCode, merged);
    }

    private List<Range> active(String fundCode, Instant now) {
        var saved = ranges.getIfPresent(fundCode);
        var active = new ArrayList<Range>();
        if (saved != null) for (Range range : saved) if (range.expiresAt().isAfter(now)) active.add(range);
        if (saved != null && active.size() != saved.size()) ranges.put(fundCode, active);
        return active;
    }

    private static LocalDate max(LocalDate left, LocalDate right) { return left.isAfter(right) ? left : right; }

    record Range(LocalDate startDate, LocalDate endDate, Instant expiresAt) {}
}
