package com.ariadne.presentation;

import com.ariadne.extraction.FundService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/funds")
public class FundController {
    private final FundService fundService;

    public FundController(FundService fundService) { this.fundService = fundService; }

    @GetMapping("/{fundCode}/navs")
    public List<FundNavResponse> navs(
            @PathVariable String fundCode,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) { return response(fundService.getHistory(fundCode, startDate, endDate)); }

    @PostMapping("/refresh")
    public void refreshFunds() { fundService.refreshFunds(); }

    @PostMapping("/{fundCode}/navs/refresh")
    public List<FundNavResponse> refreshNavs(
            @PathVariable String fundCode,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) { return response(fundService.refreshHistory(fundCode, startDate, endDate)); }

    private static List<FundNavResponse> response(List<com.ariadne.extraction.FundNav> navs) {
        return navs.stream().map(nav -> new FundNavResponse(nav.id().fundCode(), nav.id().navDate().toString(), nav.unitNav().stripTrailingZeros().toPlainString())).toList();
    }
}
