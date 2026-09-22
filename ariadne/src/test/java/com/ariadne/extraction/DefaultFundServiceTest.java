package com.ariadne.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultFundServiceTest {
    @Test
    void fetchesOnlyUncoveredPartOfAnOverlappingRequest() {
        var source = mock(FundSource.class);
        var repository = mock(FundRepository.class);
        var persistence = mock(FundPersistence.class);
        var writer = mock(FundWriter.class);
        var cache = new CheckedRangeCache(Duration.ofMinutes(5), Duration.ofHours(24));
        var clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var service = new DefaultFundService(source, repository, persistence, writer, cache, clock, LocalDate.of(2025, 1, 1));

        when(repository.existsById("000001")).thenReturn(true);
        when(source.getHistory(any(), any(), any())).thenReturn(List.of());
        when(persistence.findNavs(any(), any(), any())).thenReturn(List.of());

        service.getHistory("000001", "2026-01-01", "2026-01-03");
        service.getHistory("000001", "2026-01-02", "2026-01-05");

        verify(source).getHistory("000001", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        verify(source).getHistory("000001", LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5));
        verify(source, times(2)).getHistory(eq("000001"), any(), any());
        assertEquals(List.of(), service.getHistory("000001", "2026-01-02", "2026-01-05"));
    }

    @Test
    void rejectsNonCanonicalOrOutOfBoundDates() {
        var service = service();

        assertThrows(BadRequestException.class, () -> service.getHistory("000001", "2025-1-01", "2025-01-01"));
        assertThrows(BadRequestException.class, () -> service.getHistory("000001", "2025-02-29", "2025-03-01"));
        assertThrows(BadRequestException.class, () -> service.getHistory("000001", "2024-12-31", "2025-01-01"));
        assertThrows(BadRequestException.class, () -> service.getHistory("000001", "2025-01-01", "2026-09-23"));
    }

    @Test
    void doesNotCacheAnUpstreamFailure() {
        var source = mock(FundSource.class);
        var repository = mock(FundRepository.class);
        when(repository.existsById("000001")).thenReturn(true);
        when(source.getHistory(any(), any(), any())).thenThrow(new UpstreamException("failed"));
        var service = new DefaultFundService(source, repository, mock(FundPersistence.class), mock(FundWriter.class),
                new CheckedRangeCache(Duration.ofMinutes(5), Duration.ofHours(24)), clock(), LocalDate.of(2025, 1, 1));

        assertThrows(UpstreamException.class, () -> service.getHistory("000001", "2026-01-01", "2026-01-03"));
        assertThrows(UpstreamException.class, () -> service.getHistory("000001", "2026-01-01", "2026-01-03"));

        verify(source, times(2)).getHistory("000001", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
    }

    private static DefaultFundService service() {
        return new DefaultFundService(mock(FundSource.class), mock(FundRepository.class), mock(FundPersistence.class), mock(FundWriter.class),
                new CheckedRangeCache(Duration.ofMinutes(5), Duration.ofHours(24)), clock(), LocalDate.of(2025, 1, 1));
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }
}
