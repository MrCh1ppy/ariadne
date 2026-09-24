package com.ariadne.presentation;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ariadne.analysis.AnalysisPoint;
import com.ariadne.analysis.FundAnalysis;
import com.ariadne.analysis.FundAnalysisService;
import com.ariadne.analysis.MaPeriod;
import com.ariadne.analysis.MaValue;
import com.ariadne.extraction.Fund;
import com.ariadne.extraction.FundNav;
import com.ariadne.extraction.FundNavId;
import com.ariadne.extraction.FundSearchService;
import com.ariadne.extraction.FundService;
import com.ariadne.extraction.UpstreamException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FundControllerTest {
    @Test
    void exposesFundCodePrefixSearch() throws Exception {
        var service = org.mockito.Mockito.mock(FundService.class);
        var search = org.mockito.Mockito.mock(FundSearchService.class);
        when(search.search("022")).thenReturn(List.of(new Fund("022485", "Fund", null, null, null)));
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(service, search,
                org.mockito.Mockito.mock(FundAnalysisService.class))).build();

        mvc.perform(get("/funds/search").param("prefix", "022").param("suffix", "022"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundCode", is("022485")));
        org.mockito.Mockito.verify(search).search("022");
    }

    @Test
    void treatsLegacySuffixAsPrefixAndRejectsConflictingParameters() throws Exception {
        var search = org.mockito.Mockito.mock(FundSearchService.class);
        when(search.search("022485")).thenReturn(List.of(new Fund("022485", "Fund", null, null, null)));
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(org.mockito.Mockito.mock(FundService.class), search,
                org.mockito.Mockito.mock(FundAnalysisService.class))).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/funds/search").param("suffix", "022485"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundCode", is("022485")));
        org.mockito.Mockito.verify(search).search("022485");

        mvc.perform(get("/funds/search").param("prefix", "022").param("suffix", "485"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoMoreInteractions(search);
    }

    @Test
    void exposesTheNavRouteWithoutLeakingUpstreamDetails() throws Exception {
        var service = org.mockito.Mockito.mock(FundService.class);
        when(service.getHistory("022485", "2025-01-01", "2025-01-31")).thenReturn(List.of(
                new FundNav(new FundNavId("022485", LocalDate.of(2025, 1, 2)), new BigDecimal("1.2345000000"))));
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(service, org.mockito.Mockito.mock(FundSearchService.class), org.mockito.Mockito.mock(FundAnalysisService.class))).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/funds/022485/navs").param("startDate", "2025-01-01").param("endDate", "2025-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fundCode", is("022485")))
                .andExpect(jsonPath("$[0].unitNav", is("1.2345")));

        when(service.getHistory("022485", "2025-01-02", "2025-01-02")).thenThrow(new UpstreamException("connection refused: secret-host"));
        mvc.perform(get("/funds/022485/navs").param("startDate", "2025-01-02").param("endDate", "2025-01-02"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message", is("upstream unavailable")));
    }

    @Test
    void exposesTheAnalysisRouteWithOnlyMovingAverages() throws Exception {
        var analysis = org.mockito.Mockito.mock(FundAnalysisService.class);
        var averages = new EnumMap<MaPeriod, MaValue>(MaPeriod.class);
        averages.put(MaPeriod.MA5, new MaValue("1.4500000000", "0.46"));
        averages.put(MaPeriod.MA15, new MaValue("1.4550000000", "0.11"));
        averages.put(MaPeriod.MA30, new MaValue("1.4564866667", "0.01"));
        averages.put(MaPeriod.MA60, new MaValue("1.4627900000", "-0.42"));
        averages.put(MaPeriod.MA120, new MaValue(null, null));
        var point = new AnalysisPoint(LocalDate.of(2026, 9, 22), "1.4566000000",
                averages);
        when(analysis.analyze("022485", "2026-08-25", "2026-09-22"))
                .thenReturn(new FundAnalysis("022485", LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 22),
                        List.of(point), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(org.mockito.Mockito.mock(FundService.class),
                org.mockito.Mockito.mock(FundSearchService.class), analysis)).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/funds/022485/analysis")
                        .param("startDate", "2026-08-25")
                        .param("endDate", "2026-09-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].unitNav", is("1.4566000000")))
                .andExpect(jsonPath("$.points[0].movingAverages.MA5.value", is("1.4500000000")))
                .andExpect(jsonPath("$.points[0].movingAverages.MA15.deviationPercent", is("0.11")))
                .andExpect(jsonPath("$.points[0].movingAverages.MA30.value", is("1.4564866667")))
                .andExpect(jsonPath("$.points[0].movingAverages.MA30.deviationPercent", is("0.01")))
                .andExpect(jsonPath("$.points[0].movingAverages.MA120.value").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.points[0].ma30").doesNotExist())
                .andExpect(jsonPath("$.points[0].navVsMa30Percent").doesNotExist());
        org.mockito.Mockito.verify(analysis).analyze("022485", "2026-08-25", "2026-09-22");

        when(analysis.analyze("022485", "2026-08-25", "2026-09-22", java.util.Set.of(MaPeriod.MA5, MaPeriod.MA15)))
                .thenReturn(new FundAnalysis("022485", LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 22),
                        List.of(point), List.of()));
        mvc.perform(get("/funds/022485/analysis")
                        .param("startDate", "2026-08-25").param("endDate", "2026-09-22")
                        .param("periods", "MA5,MA15"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(analysis).analyze("022485", "2026-08-25", "2026-09-22",
                java.util.Set.of(MaPeriod.MA5, MaPeriod.MA15));
    }

    @Test
    void rejectsInvalidPeriodName() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(org.mockito.Mockito.mock(FundService.class),
                org.mockito.Mockito.mock(FundSearchService.class), org.mockito.Mockito.mock(FundAnalysisService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/funds/022485/analysis")
                        .param("startDate", "2026-08-25")
                        .param("endDate", "2026-09-22")
                        .param("periods", "MA999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("invalid MA period: MA999")));
        mvc.perform(get("/funds/022485/analysis")
                        .param("startDate", "2026-08-25")
                        .param("endDate", "2026-09-22")
                        .param("periods", "MA30,"))
                .andExpect(status().isBadRequest());
    }
}
