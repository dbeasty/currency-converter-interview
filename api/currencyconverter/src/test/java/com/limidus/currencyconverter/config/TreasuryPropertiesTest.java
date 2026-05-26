package com.limidus.currencyconverter.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TreasuryPropertiesTest {

    @Test
    void gettersAndSetters() {
        TreasuryProperties p = new TreasuryProperties();
        assertThat(p.getBaseUrl()).contains("treasury.gov");
        assertThat(p.getTimezone()).isEqualTo("America/New_York");
        assertThat(p.isBulkLoadEnabled()).isFalse();
        assertThat(p.getBulkLoadCron()).isEqualTo("0 30 9 * * *");
        assertThat(p.isInProcessCacheDisabled()).isFalse();
        assertThat(p.isDbLookupDisabled()).isFalse();

        p.setBaseUrl("https://example.test");
        p.setTimezone("UTC");
        p.setBulkLoadEnabled(true);
        p.setBulkLoadCron("0 0 12 * * *");
        p.setInProcessCacheDisabled(true);
        p.setDbLookupDisabled(true);

        assertThat(p.getBaseUrl()).isEqualTo("https://example.test");
        assertThat(p.getTimezone()).isEqualTo("UTC");
        assertThat(p.isBulkLoadEnabled()).isTrue();
        assertThat(p.getBulkLoadCron()).isEqualTo("0 0 12 * * *");
        assertThat(p.isInProcessCacheDisabled()).isTrue();
        assertThat(p.isDbLookupDisabled()).isTrue();
    }
}
