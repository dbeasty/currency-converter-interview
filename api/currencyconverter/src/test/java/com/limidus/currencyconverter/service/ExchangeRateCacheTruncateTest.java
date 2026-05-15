package com.limidus.currencyconverter.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExchangeRateCacheTruncateTest {

    @Test
    void truncate_null() {
        assertThat(ExchangeRateCache.truncate(null)).isEqualTo("(null)");
    }

    @Test
    void truncate_shortUnchanged() {
        assertThat(ExchangeRateCache.truncate("hi")).isEqualTo("hi");
    }

    @Test
    void truncate_longAppendsIndicator() {
        String longStr = "x".repeat(600);
        String out = ExchangeRateCache.truncate(longStr);
        assertThat(out).contains("truncated");
        assertThat(out.length()).isLessThan(longStr.length());
    }
}
