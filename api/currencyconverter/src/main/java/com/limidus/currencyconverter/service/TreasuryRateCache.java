package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link TreasuryApiClient} kept for test-injection convenience.
 * Caching is now handled at the {@link ExchangeRateService} level, which covers both
 * DB-sourced and API-sourced results with a single {@code @Cacheable} entry point.
 */
@Component
public class TreasuryRateCache {

    private final TreasuryApiClient treasuryApiClient;

    public TreasuryRateCache(TreasuryApiClient treasuryApiClient) {
        this.treasuryApiClient = treasuryApiClient;
    }

    public Optional<TreasuryRateRow> load(String countryCurrencyDesc, LocalDate purchaseDate, LocalDate asOfDate) {
        LocalDate windowStartInclusive = purchaseDate.minusMonths(6);
        return treasuryApiClient.fetchBestRateWithinWindow(countryCurrencyDesc, purchaseDate, windowStartInclusive);
    }
}
