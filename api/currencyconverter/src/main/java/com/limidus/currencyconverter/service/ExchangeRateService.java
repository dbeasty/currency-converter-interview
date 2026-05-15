package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.config.TreasuryProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {

    private final TreasuryRateCache treasuryRateCache;
    private final ZoneId treasuryZone;

    public ExchangeRateService(TreasuryRateCache treasuryRateCache, TreasuryProperties treasuryProperties) {
        this.treasuryRateCache = treasuryRateCache;
        this.treasuryZone = ZoneId.of(treasuryProperties.getTimezone());
    }

    /**
     * Latest qualifying Treasury rate for {@code purchaseDate}. Delegates to {@link TreasuryRateCache}
     * with today's date in Treasury's timezone (ET) as the {@code asOfDate} component of the cache
     * key. This ensures a cache miss fires when Treasury's calendar day rolls over in ET rather than
     * UTC, keeping the cached rate aligned with Treasury's publication schedule.
     */
    public Optional<TreasuryRateRow> findMostRecentRate(String countryCurrencyDesc, LocalDate purchaseDate) {
        TreasuryRateRow row = treasuryRateCache.load(countryCurrencyDesc, purchaseDate, LocalDate.now(treasuryZone));
        return Optional.ofNullable(row);
    }
}
