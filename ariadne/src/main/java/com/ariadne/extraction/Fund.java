package com.ariadne.extraction;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("fund")
public record Fund(
        @Id @Column("fund_code") String fundCode,
        @Column("fund_name") String fundName,
        @Column("fund_type") String fundType,
        @Column("pinyin_abbreviation") String pinyinAbbreviation,
        @Column("pinyin_full_name") String pinyinFullName
) {}
