package com.ariadne.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckedRangeCacheTest {
    @Test
    void expiresCheckedRangesAtTheirConfiguredTtl() {
        var cache = new CheckedRangeCache(Duration.ofMinutes(5), Duration.ofHours(24));
        var range = new DateRange(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));
        var now = Instant.parse("2026-01-03T00:00:00Z");

        cache.markChecked("000001", range, LocalDate.of(2026, 1, 3), now);

        assertEquals(List.of(), cache.missing("000001", range, now.plus(Duration.ofMinutes(4))));
        assertEquals(List.of(range), cache.missing("000001", range, now.plus(Duration.ofMinutes(6))));
    }
}
