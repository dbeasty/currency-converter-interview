package com.limidus.currencyconverter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.repository.ExchangeRateRepository;
import com.limidus.currencyconverter.service.ExchangeRateCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "app.treasury.in-process-cache-disabled=true")
class ExchangeRateCacheInProcessCacheDisabledSpringTest {

    @Autowired
    private ExchangeRateCache exchangeRateCache;

    @MockitoBean
    private ExchangeRateRepository exchangeRateRepository;

    @MockitoBean
    private TreasuryApiClient treasuryApiClient;

    @Test
    void load_queriesDbOnEachIdenticalRequestWhenInProcessCacheDisabled() {
        String currency = "FLAG-NO-IP-CACHE";
        LocalDate purchase = LocalDate.of(2024, 6, 10);
        LocalDate asOf = LocalDate.of(2024, 6, 11);
        ExchangeRate fromDb = ExchangeRate.builder()
                .id(UUID.randomUUID())
                .currency(currency)
                .rate(new BigDecimal("1.25"))
                .effectiveDate(LocalDate.of(2024, 6, 1))
                .sourceTimestamp(Instant.parse("2024-06-01T00:00:00Z"))
                .build();
        when(exchangeRateRepository.findMostRecentInWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.of(fromDb));

        exchangeRateCache.load(currency, purchase, asOf);
        exchangeRateCache.load(currency, purchase, asOf);

        verify(exchangeRateRepository, times(2)).findMostRecentInWindow(eq(currency), eq(purchase), any());
        verify(treasuryApiClient, never()).fetchBestRateWithinWindow(any(), any(), any());
    }
}
