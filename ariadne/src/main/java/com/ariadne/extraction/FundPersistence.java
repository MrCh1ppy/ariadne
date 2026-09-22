package com.ariadne.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FundPersistence {
    private final JdbcClient jdbcClient;

    public FundPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<FundNav> findNavs(String fundCode, LocalDate startDate, LocalDate endDate) {
        return jdbcClient.sql("""
                SELECT fund_code, nav_date, unit_nav FROM fund_nav
                WHERE fund_code = :fundCode AND nav_date BETWEEN :startDate AND :endDate
                ORDER BY nav_date
                """)
                .param("fundCode", fundCode)
                .param("startDate", startDate)
                .param("endDate", endDate)
                .query((rs, rowNum) -> new FundNav(
                        new FundNavId(rs.getString("fund_code").trim(), rs.getObject("nav_date", LocalDate.class)),
                        rs.getBigDecimal("unit_nav")
                )).list();
    }

    public void upsertFunds(List<Fund> funds) {
        for (Fund fund : funds) {
            jdbcClient.sql("""
                    INSERT INTO fund (fund_code, fund_name, fund_type, pinyin_abbreviation, pinyin_full_name)
                    VALUES (:fundCode, :fundName, :fundType, :pinyinAbbreviation, :pinyinFullName)
                    ON CONFLICT (fund_code) DO UPDATE SET
                        fund_name = EXCLUDED.fund_name,
                        fund_type = EXCLUDED.fund_type,
                        pinyin_abbreviation = EXCLUDED.pinyin_abbreviation,
                        pinyin_full_name = EXCLUDED.pinyin_full_name
                    """)
                    .param("fundCode", fund.fundCode())
                    .param("fundName", fund.fundName())
                    .param("fundType", fund.fundType())
                    .param("pinyinAbbreviation", fund.pinyinAbbreviation())
                    .param("pinyinFullName", fund.pinyinFullName())
                    .update();
        }
    }

    public void upsertNavs(List<FundNav> navs) {
        for (FundNav nav : navs) {
            jdbcClient.sql("""
                    INSERT INTO fund_nav (fund_code, nav_date, unit_nav) VALUES (:fundCode, :navDate, :unitNav)
                    ON CONFLICT (fund_code, nav_date) DO UPDATE SET unit_nav = EXCLUDED.unit_nav
                    """)
                    .param("fundCode", nav.id().fundCode())
                    .param("navDate", nav.id().navDate())
                    .param("unitNav", nav.unitNav())
                    .update();
        }
    }
}
