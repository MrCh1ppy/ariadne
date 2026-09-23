package com.ariadne.analysis;

import java.time.LocalDate;
import java.util.List;

public record FundAnalysis(
        String fundCode,
        LocalDate startDate,
        LocalDate endDate,
        List<AnalysisPoint> points,
        List<String> warnings
) {}
