package com.limidus.currencyconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
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

@SpringBootTest(properties = "app.treasury.db-lookup-disabled=true")
class ExchangeRateCacheDbLookupDisabledSpringTest {

    @Autowired
    private ExchangeRateCache exchangeRateCache;

    @MockitoBean
    private ExchangeRateRepository exchangeRateRepository;

    @MockitoBean
    private TreasuryApiClient treasuryApiClient;

    @Test
    void load_skipsDbReadAndCallsTreasuryEvenWhenDbWouldHaveRow() {
        String currency = "FLAG-DB-SKIP";
        LocalDate purchase = LocalDate.of(2024, 4, 10);
        LocalDate asOf = LocalDate.of(2024, 4, 11);

        TreasuryRateRow row = new TreasuryRateRow(currency, "1.5", "2024-04-01", "2024-04-01");
        when(treasuryApiClient.fetchBestRateWithinWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.of(row));
        when(exchangeRateRepository.findByCurrencyAndEffectiveDate(eq(currency), eq(LocalDate.of(2024, 4, 1))))
                .thenReturn(Optional.empty());
        ExchangeRate saved = ExchangeRate.builder()
                .id(UUID.randomUUID())
                .currency(currency)
                .rate(new BigDecimal("1.500000"))
                .effectiveDate(LocalDate.of(2024, 4, 1))
                .sourceTimestamp(Instant.now())
                .build();
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenReturn(saved);

        ExchangeRate out = exchangeRateCache.load(currency, purchase, asOf);

        assertThat(out).isNotNull();
        verify(exchangeRateRepository, never()).findMostRecentInWindow(any(), any(), any());
        verify(treasuryApiClient).fetchBestRateWithinWindow(eq(currency), eq(purchase), any());
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }
}
