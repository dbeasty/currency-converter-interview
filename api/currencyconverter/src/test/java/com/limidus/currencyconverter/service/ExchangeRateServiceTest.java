package com.limidus.currencyconverter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.domain.ExchangeRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateCache exchangeRateCache;

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void init() {
        TreasuryProperties props = new TreasuryProperties();
        props.setTimezone("UTC");
        exchangeRateService = new ExchangeRateService(exchangeRateCache, props);
    }

    @Test
    void findMostRecentRate_delegatesToCache() {
        LocalDate purchase = LocalDate.of(2024, 2, 1);
        ExchangeRate rate = ExchangeRate.builder()
                .id(UUID.randomUUID())
                .currency("X")
                .rate(new BigDecimal("1.1"))
                .effectiveDate(purchase)
                .build();
        when(exchangeRateCache.load(eq("X"), eq(purchase), any(LocalDate.class))).thenReturn(Optional.of(rate));

        assertThat(exchangeRateService.findMostRecentRate("X", purchase)).contains(rate);
        verify(exchangeRateCache).load(eq("X"), eq(purchase), any(LocalDate.class));
    }

    @Test
    void findMostRecentRate_emptyFromCache() {
        LocalDate purchase = LocalDate.of(2024, 2, 1);
        when(exchangeRateCache.load(eq("Y"), eq(purchase), any(LocalDate.class))).thenReturn(Optional.empty());

        assertThat(exchangeRateService.findMostRecentRate("Y", purchase)).isEmpty();
    }
}
