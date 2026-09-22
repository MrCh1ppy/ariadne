package com.ariadne.extraction;

import java.time.LocalDate;
import java.util.List;

public interface FundService {
    List<FundNav> getHistory(String fundCode, String startDate, String endDate);
    void refreshFunds();
    List<FundNav> refreshHistory(String fundCode, String startDate, String endDate);
}
