package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {

    private final TreasuryRateCache treasuryRateCache;

    public ExchangeRateService(TreasuryRateCache treasuryRateCache) {
        this.treasuryRateCache = treasuryRateCache;
    }

    /**
     * Latest qualifying Treasury rate for {@code purchaseDate}. Delegates to {@link TreasuryRateCache}
     * with {@code LocalDate.now()} so the cache key includes today: first request each day hits the
     * API; further requests the same day reuse the cached rate.
     */
    public Optional<TreasuryRateRow> findBestRate(String countryCurrencyDesc, LocalDate purchaseDate) {
        TreasuryRateRow row = treasuryRateCache.load(countryCurrencyDesc, purchaseDate, LocalDate.now());
        return Optional.ofNullable(row);
    }
}
