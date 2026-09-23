package com.ariadne.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class FundSearchServiceTest {
    @Test
    void matchesPrefixAndKeepsLeadingZero() {
        var persistence = mock(FundPersistence.class);
        var funds = mock(FundService.class);
        var match = new Fund("022485", "Fund", null, null, null);
        when(persistence.searchFunds("022")).thenReturn(List.of(match));

        assertEquals(List.of(match), new FundSearchService(persistence, funds).search("022"));
        verify(funds, never()).refreshFunds();
    }

    @Test
    void lazilyRefreshesAnEmptyDirectory() {
        var persistence = mock(FundPersistence.class);
        var funds = mock(FundService.class);
        var match = new Fund("022485", "Fund", null, null, null);
        when(persistence.searchFunds("022")).thenReturn(List.<Fund>of()).thenReturn(List.of(match));
        when(persistence.isFundDirectoryEmpty()).thenReturn(true);

        assertEquals(List.of(match), new FundSearchService(persistence, funds).search("022"));
        verify(funds).refreshFunds();
    }

    @Test
    void validatesPrefixLengthAndDoesNotRefreshANonemptyDirectoryWithoutMatches() {
        var persistence = mock(FundPersistence.class);
        var funds = mock(FundService.class);
        when(persistence.searchFunds("022")).thenReturn(List.<Fund>of());
        when(persistence.searchFunds("485")).thenReturn(List.<Fund>of());
        when(persistence.isFundDirectoryEmpty()).thenReturn(false);
        var service = new FundSearchService(persistence, funds);
        assertEquals(List.of(), service.search("022"));
        assertEquals(List.of(), service.search("485"));
        org.mockito.Mockito.verify(persistence).searchFunds("485");
        verify(funds, never()).refreshFunds();
        assertThrows(BadRequestException.class, () -> service.search("48"));
        assertThrows(BadRequestException.class, () -> service.search("abc"));
        assertThrows(BadRequestException.class, () -> service.search("0000000"));
    }
}
