package com.ariadne.analysis;

import java.time.LocalDate;
import java.util.Map;

public record AnalysisPoint(LocalDate date, String unitNav, Map<MaPeriod, MaValue> movingAverages) {}
