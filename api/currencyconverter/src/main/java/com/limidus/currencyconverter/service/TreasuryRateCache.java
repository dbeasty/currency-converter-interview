package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import java.time.LocalDate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Loads the latest Treasury reporting rate for a purchase date (within the six-month window) and
 * caches by currency, purchase date, and {@code asOfDate}. The first lookup each calendar day for
 * a given key misses the cache and calls the Treasury API; later same-day lookups reuse the
 * cached row.
 */
@Component
public class TreasuryRateCache {

    private final TreasuryApiClient treasuryApiClient;

    public TreasuryRateCache(TreasuryApiClient treasuryApiClient) {
        this.treasuryApiClient = treasuryApiClient;
    }

    @Cacheable(
            value = "treasuryRates",
            key = "#countryCurrencyDesc + '-' + #purchaseDate + '-' + #asOfDate",
            unless = "#result == null")
    public TreasuryRateRow load(String countryCurrencyDesc, LocalDate purchaseDate, LocalDate asOfDate) {
        LocalDate windowStartInclusive = purchaseDate.minusMonths(6);
        return treasuryApiClient
                .fetchBestRateWithinWindow(countryCurrencyDesc, purchaseDate, windowStartInclusive)
                .orElse(null);
    }
}
