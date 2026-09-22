package com.ariadne.extraction;

import java.time.LocalDate;
import java.util.List;

public interface FundSource {
    List<RemoteFund> getFunds();
    List<RemoteFundNav> getHistory(String fundCode, LocalDate startDate, LocalDate endDate);
}
