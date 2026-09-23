package com.ariadne.extraction;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FundSearchService {
    private final FundPersistence persistence;
    private final FundService fundService;

    public FundSearchService(FundPersistence persistence, FundService fundService) {
        this.persistence = persistence;
        this.fundService = fundService;
    }

    public List<Fund> search(String prefix) {
        if (prefix == null || !prefix.matches("\\d{3,6}")) {
            throw new BadRequestException("prefix must be three to six digits");
        }
        var matches = persistence.searchFunds(prefix);
        if (matches.isEmpty() && persistence.isFundDirectoryEmpty()) {
            fundService.refreshFunds();
            matches = persistence.searchFunds(prefix);
        }
        return matches;
    }
}
