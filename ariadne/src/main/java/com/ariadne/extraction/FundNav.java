package com.ariadne.extraction;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("fund_nav")
public record FundNav(
        @Id FundNavId id,
        @Column("unit_nav") BigDecimal unitNav
) {}
