package com.ariadne.presentation;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ariadne.extraction.FundNav;
import com.ariadne.extraction.FundNavId;
import com.ariadne.extraction.FundService;
import com.ariadne.extraction.UpstreamException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FundControllerTest {
    @Test
    void exposesTheNavRouteWithoutLeakingUpstreamDetails() throws Exception {
        var service = org.mockito.Mockito.mock(FundService.class);
        when(service.getHistory("022485", "2025-01-01", "2025-01-31")).thenReturn(List.of(
                new FundNav(new FundNavId("022485", LocalDate.of(2025, 1, 2)), new BigDecimal("1.2345000000"))));
        var mvc = MockMvcBuilders.standaloneSetup(new FundController(service)).setControllerAdvice(new ApiExceptionHandler()).build();

        mvc.perform(get("/funds/022485/navs").param("startDate", "2025-01-01").param("endDate", "2025-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fundCode", is("022485")))
                .andExpect(jsonPath("$[0].unitNav", is("1.2345")));

        when(service.getHistory("022485", "2025-01-02", "2025-01-02")).thenThrow(new UpstreamException("connection refused: secret-host"));
        mvc.perform(get("/funds/022485/navs").param("startDate", "2025-01-02").param("endDate", "2025-01-02"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message", is("upstream unavailable")));
    }
}
