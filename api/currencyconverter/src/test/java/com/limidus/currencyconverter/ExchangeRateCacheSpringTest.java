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

@SpringBootTest
class ExchangeRateCacheSpringTest {

    @Autowired
    private ExchangeRateCache exchangeRateCache;

    @MockitoBean
    private ExchangeRateRepository exchangeRateRepository;

    @MockitoBean
    private TreasuryApiClient treasuryApiClient;

    @Test
    void load_returnsDbHitWithoutCallingTreasury() {
        String currency = "CACHE-DB-1";
        LocalDate purchase = LocalDate.of(2024, 4, 10);
        LocalDate asOf = LocalDate.of(2024, 4, 11);
        ExchangeRate fromDb = ExchangeRate.builder()
                .id(UUID.randomUUID())
                .currency(currency)
                .rate(new BigDecimal("1.25"))
                .effectiveDate(LocalDate.of(2024, 4, 1))
                .sourceTimestamp(Instant.parse("2024-04-01T00:00:00Z"))
                .build();
        when(exchangeRateRepository.findMostRecentInWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.of(fromDb));

        Optional<ExchangeRate> out = exchangeRateCache.load(currency, purchase, asOf);

        assertThat(out).contains(fromDb);
        verify(treasuryApiClient, never()).fetchBestRateWithinWindow(any(), any(), any());
    }

    @Test
    void load_whenDbThrows_fallsBackToTreasuryAndPersists() {
        String currency = "CACHE-DB-2";
        LocalDate purchase = LocalDate.of(2024, 5, 10);
        LocalDate asOf = LocalDate.of(2024, 5, 11);
        when(exchangeRateRepository.findMostRecentInWindow(eq(currency), eq(purchase), any()))
                .thenThrow(new RuntimeException("simulated db failure"));

        TreasuryRateRow row = new TreasuryRateRow(currency, "2.0", "2024-05-01", "2024-05-01");
        when(treasuryApiClient.fetchBestRateWithinWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.of(row));
        when(exchangeRateRepository.findByCurrencyAndEffectiveDate(eq(currency), eq(LocalDate.of(2024, 5, 1))))
                .thenReturn(Optional.empty());
        ExchangeRate saved = ExchangeRate.builder()
                .id(UUID.randomUUID())
                .currency(currency)
                .rate(new BigDecimal("2.000000"))
                .effectiveDate(LocalDate.of(2024, 5, 1))
                .sourceTimestamp(Instant.now())
                .build();
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenReturn(saved);

        Optional<ExchangeRate> out = exchangeRateCache.load(currency, purchase, asOf);

        assertThat(out).isPresent();
        verify(treasuryApiClient).fetchBestRateWithinWindow(eq(currency), eq(purchase), any());
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void load_whenTreasuryEmpty_returnsEmpty() {
        String currency = "CACHE-DB-3";
        LocalDate purchase = LocalDate.of(2024, 7, 10);
        LocalDate asOf = LocalDate.of(2024, 7, 11);
        when(exchangeRateRepository.findMostRecentInWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.empty());
        when(treasuryApiClient.fetchBestRateWithinWindow(eq(currency), eq(purchase), any()))
                .thenReturn(Optional.empty());

        assertThat(exchangeRateCache.load(currency, purchase, asOf)).isEmpty();
    }
}
