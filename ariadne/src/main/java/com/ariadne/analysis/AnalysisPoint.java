package com.ariadne.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Map;

public record AnalysisPoint(LocalDate date, String unitNav, Map<MaPeriod, MaValue> movingAverages) {
    @JsonProperty
    String ma30() { return flat(MaPeriod.MA30); }

    @JsonProperty
    String navVsMa30Percent() { return flatPercent(MaPeriod.MA30); }

    @JsonProperty
    String ma60() { return flat(MaPeriod.MA60); }

    @JsonProperty
    String navVsMa60Percent() { return flatPercent(MaPeriod.MA60); }

    private String flat(MaPeriod period) {
        var value = movingAverages.get(period);
        return value == null ? null : value.value();
    }

    private String flatPercent(MaPeriod period) {
        var value = movingAverages.get(period);
        return value == null ? null : value.deviationPercent();
    }
}
