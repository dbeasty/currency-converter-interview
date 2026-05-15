package com.limidus.currencyconverter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkRateLoaderTest {

    @Mock
    private TreasuryApiClient treasuryApiClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private TreasuryProperties treasuryProperties;
    private BulkRateLoader loader;

    @BeforeEach
    void init() {
        treasuryProperties = new TreasuryProperties();
        treasuryProperties.setTimezone("America/New_York");
        treasuryProperties.setBulkLoadEnabled(false);
        loader = new BulkRateLoader(treasuryApiClient, exchangeRateRepository, treasuryProperties);
    }

    @Test
    void onStartup_whenBulkDisabled_doesNotFetch() {
        loader.onStartup();
        verify(treasuryApiClient, never()).fetchAllRatesInWindow(any(), any());
    }

    @Test
    void onStartup_whenBulkEnabled_runsLoad() {
        treasuryProperties.setBulkLoadEnabled(true);
        loader = new BulkRateLoader(treasuryApiClient, exchangeRateRepository, treasuryProperties);
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any())).thenReturn(List.of());
        loader.onStartup();
        verify(treasuryApiClient).fetchAllRatesInWindow(any(), any());
    }

    @Test
    void onSchedule_whenBulkEnabled_runsLoad() {
        treasuryProperties.setBulkLoadEnabled(true);
        loader = new BulkRateLoader(treasuryApiClient, exchangeRateRepository, treasuryProperties);
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any())).thenReturn(List.of());
        loader.onSchedule();
        verify(treasuryApiClient).fetchAllRatesInWindow(any(), any());
    }

    @Test
    void onSchedule_whenBulkDisabled_skips() {
        loader.onSchedule();
        verify(treasuryApiClient, never()).fetchAllRatesInWindow(any(), any());
    }

    @Test
    void load_emptyTreasuryRows_returnsEarly() {
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any())).thenReturn(List.of());
        loader.load();
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void load_insertsNewRate() {
        LocalDate eff = LocalDate.of(2024, 3, 1);
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any()))
                .thenReturn(List.of(new TreasuryRateRow("Z-Currency", "2.5", "2024-03-01", eff.toString())));
        when(exchangeRateRepository.findAllInWindow(any())).thenReturn(List.of());

        loader.load();

        ArgumentCaptor<ExchangeRate> cap = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(cap.capture());
        assertThat(cap.getValue().getCurrency()).isEqualTo("Z-Currency");
        assertThat(cap.getValue().getRate()).isEqualByComparingTo("2.500000");
    }

    @Test
    void load_updatesWhenRateChanged() {
        LocalDate eff = LocalDate.of(2024, 3, 1);
        UUID id = UUID.randomUUID();
        ExchangeRate existing = ExchangeRate.builder()
                .id(id)
                .currency("Z-Currency")
                .rate(new BigDecimal("1.000000"))
                .effectiveDate(eff)
                .build();
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any()))
                .thenReturn(List.of(new TreasuryRateRow("Z-Currency", "3.0", "2024-03-01", eff.toString())));
        when(exchangeRateRepository.findAllInWindow(any())).thenReturn(List.of(existing));

        loader.load();

        verify(exchangeRateRepository).save(existing);
        assertThat(existing.getRate()).isEqualByComparingTo("3.000000");
    }

    @Test
    void load_skipsWhenRateUnchanged() {
        LocalDate eff = LocalDate.of(2024, 3, 1);
        UUID id = UUID.randomUUID();
        ExchangeRate existing = ExchangeRate.builder()
                .id(id)
                .currency("Z-Currency")
                .rate(new BigDecimal("2.500000"))
                .effectiveDate(eff)
                .build();
        when(treasuryApiClient.fetchAllRatesInWindow(any(), any()))
                .thenReturn(List.of(new TreasuryRateRow("Z-Currency", "2.5", "2024-03-01", eff.toString())));
        when(exchangeRateRepository.findAllInWindow(any())).thenReturn(List.of(existing));

        loader.load();

        verify(exchangeRateRepository, never()).save(any());
    }
}
