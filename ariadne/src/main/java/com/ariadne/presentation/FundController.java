package com.ariadne.presentation;

import com.ariadne.analysis.FundAnalysis;
import com.ariadne.analysis.FundAnalysisService;
import com.ariadne.extraction.FundService;
import com.ariadne.extraction.FundSearchService;
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
    private final FundSearchService searchService;
    private final FundAnalysisService analysisService;

    public FundController(FundService fundService, FundSearchService searchService, FundAnalysisService analysisService) {
        this.fundService = fundService;
        this.searchService = searchService;
        this.analysisService = analysisService;
    }

    @GetMapping("/search")
    public List<FundSearchResponse> search(
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) String suffix
    ) {
        if (prefix != null && suffix != null && !prefix.equals(suffix)) {
            throw new com.ariadne.extraction.BadRequestException("prefix and suffix must match when both are supplied");
        }
        var codePrefix = prefix != null ? prefix : suffix;
        return searchService.search(codePrefix).stream().map(fund -> new FundSearchResponse(fund.fundCode(), fund.fundName())).toList();
    }

    @GetMapping("/{fundCode}/navs")
    public List<FundNavResponse> navs(
            @PathVariable String fundCode,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) { return response(fundService.getHistory(fundCode, startDate, endDate)); }

    @GetMapping("/{fundCode}/analysis")
    public FundAnalysis analysis(
            @PathVariable String fundCode,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) { return analysisService.analyze(fundCode, startDate, endDate); }

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
