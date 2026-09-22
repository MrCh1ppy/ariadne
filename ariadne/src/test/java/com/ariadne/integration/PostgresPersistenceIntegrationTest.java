package com.ariadne.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ariadne.extraction.Fund;
import com.ariadne.extraction.FundNav;
import com.ariadne.extraction.FundNavId;
import com.ariadne.extraction.FundPersistence;
import com.ariadne.extraction.FundRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "spring.sql.init.mode=never")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "ARIADNE_INTEGRATION_DB", matches = "true")
class PostgresPersistenceIntegrationTest {
    private static final String SCHEMA = System.getenv("ARIADNE_TEST_SCHEMA");

    @Autowired JdbcClient jdbc;
    @Autowired FundRepository funds;
    @Autowired FundPersistence persistence;

    @BeforeAll
    void createTables() {
        if (SCHEMA == null || !SCHEMA.matches("ariadne_it_[a-z0-9_]+")) throw new IllegalStateException("invalid integration schema");
        jdbc.sql("CREATE TABLE fund (fund_code char(6) PRIMARY KEY CHECK (fund_code ~ '^[0-9]{6}$'), fund_name text NOT NULL, fund_type text, pinyin_abbreviation text, pinyin_full_name text)").update();
        jdbc.sql("CREATE TABLE fund_nav (fund_code char(6) NOT NULL REFERENCES fund(fund_code), nav_date date NOT NULL, unit_nav numeric(20,10) NOT NULL CHECK (unit_nav > 0), PRIMARY KEY (fund_code, nav_date))").update();
    }

    @AfterAll
    void dropTables() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void mapsSpringDataIdAndPersistsJdbcClientDecimalAndForeignKey() {
        var fund = new Fund("123456", "Integration Fund", "type", "IF", "Integration Fund");
        persistence.upsertFunds(List.of(fund));
        assertEquals(fund, funds.findById("123456").orElseThrow());

        var value = new BigDecimal("1234567890.1234567890");
        persistence.upsertNavs(List.of(new FundNav(new FundNavId("123456", LocalDate.of(2025, 1, 2)), value)));
        assertEquals(value, persistence.findNavs("123456", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)).getFirst().unitNav());

        assertThrows(DataIntegrityViolationException.class, () -> persistence.upsertNavs(List.of(
                new FundNav(new FundNavId("999999", LocalDate.of(2025, 1, 2)), BigDecimal.ONE))));
    }
}
