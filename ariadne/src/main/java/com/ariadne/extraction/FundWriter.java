package com.ariadne.extraction;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FundWriter {
    private final FundPersistence persistence;

    public FundWriter(FundPersistence persistence) { this.persistence = persistence; }

    @Transactional
    public void saveFunds(List<Fund> funds) { persistence.upsertFunds(funds); }

    @Transactional
    public void saveNavs(List<FundNav> navs) { persistence.upsertNavs(navs); }
}
