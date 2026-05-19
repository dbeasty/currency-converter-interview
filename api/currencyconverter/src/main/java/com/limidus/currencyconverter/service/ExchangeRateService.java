package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.domain.ExchangeRate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateCache exchangeRateCache;
    private final ZoneId treasuryZone;

    public ExchangeRateService(ExchangeRateCache exchangeRateCache, TreasuryProperties treasuryProperties) {
        this.exchangeRateCache = exchangeRateCache;
        this.treasuryZone = ZoneId.of(treasuryProperties.getTimezone());
    }

    /**
     * 3-tier lookup: in-process cache → database → Treasury API.
     *
     * <p>Delegates to {@link ExchangeRateCache#load} with today's date in Treasury's ET timezone
     * as the {@code asOfDate} cache-key component. Keeping the timezone computation here (outside
     * the {@code @Cacheable} proxy) avoids SpEL field-access issues on the proxy object.
     */
    public Optional<ExchangeRate> findMostRecentRate(String currency, LocalDate purchaseDate) {
        LocalDate asOfDate = LocalDate.now(treasuryZone);
        String cacheKey = currency + "-" + purchaseDate + "-" + asOfDate;
        log.debug("[CACHE LOOKUP] Requesting rate for key={}", cacheKey);
        Optional<ExchangeRate> result =
                Optional.ofNullable(exchangeRateCache.load(currency, purchaseDate, asOfDate));
        if (result.isPresent()) {
            // If the cache served this from memory the ExchangeRateCache body was skipped entirely;
            // we log here to capture both hit and miss paths at the service boundary.
            log.debug("[CACHE RESULT] key={} — found effectiveDate={} rate={}",
                    cacheKey, result.get().getEffectiveDate(), result.get().getRate());
        } else {
            log.warn("[CACHE RESULT] key={} — no qualifying rate found", cacheKey);
        }
        return result;
    }
}
