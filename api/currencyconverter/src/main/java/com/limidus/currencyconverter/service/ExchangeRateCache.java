package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-process caching layer for the 3-tier exchange rate lookup.
 *
 * <p>The {@code @Cacheable} annotation lives here rather than on {@link ExchangeRateService} so
 * the cache key can reference {@code #asOfDate} as a plain method parameter. Putting it on the
 * service and accessing {@code treasuryZone} via {@code #root.target.treasuryZone} in SpEL fails
 * at runtime because Spring's AOP proxy makes the package-private field invisible to SpEL
 * reflection.
 *
 * <p>Cache key: {@code currency + purchaseDate + asOfDate (today in Treasury's ET timezone)}.
 * Including today's date causes a daily cache miss that picks up newly published rates without any
 * manual eviction.
 */
@Component
public class ExchangeRateCache {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateCache.class);

    /** Responses larger than this character count are truncated in log output. */
    private static final int LOG_PAYLOAD_MAX = 500;

    private final TreasuryApiClient treasuryApiClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateCache(
            TreasuryApiClient treasuryApiClient,
            ExchangeRateRepository exchangeRateRepository) {
        this.treasuryApiClient = treasuryApiClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Loads the best qualifying exchange rate for {@code currency} and {@code purchaseDate},
     * checking tiers in order:
     *
     * <ol>
     *   <li>In-process cache (this method's {@code @Cacheable}) — zero I/O on a hit.
     *   <li>Database — populated by prior conversions and the optional bulk loader.
     *   <li>Treasury Fiscal Data API — only on a combined cache + DB miss; result is saved to DB.
     * </ol>
     *
     * {@code null} results (no qualifying rate) are never cached ({@code unless = "#result == null"})
     * so a transient data gap does not get locked in.
     * When this method body executes it always means a cache miss; a hit short-circuits before entry.
     */
    @Cacheable(
            value = "treasuryRates",
            key = "#currency + '-' + #purchaseDate + '-' + #asOfDate",
            unless = "#result == null")
    @Transactional
    public ExchangeRate load(String currency, LocalDate purchaseDate, LocalDate asOfDate) {
        String cacheKey = currency + "-" + purchaseDate + "-" + asOfDate;
        log.info("[CACHE MISS] key={} — proceeding to DB lookup", cacheKey);

        LocalDate windowStart = purchaseDate.minusMonths(6);

        // Tier 2: DB
        Optional<ExchangeRate> dbResult = queryDb(currency, purchaseDate, windowStart);
        if (dbResult.isPresent()) {
            ExchangeRate er = dbResult.get();
            log.info("[DB HIT] currency={} purchaseDate={} effectiveDate={} rate={} id={}",
                    currency, purchaseDate, er.getEffectiveDate(), er.getRate(), er.getId());
            return er;
        }

        // Tier 3: Treasury API
        log.info("[TREASURY API] Fetching rate: currency={} purchaseDate={} windowStart={}",
                currency, purchaseDate, windowStart);
        TreasuryRateRow row = treasuryApiClient
                .fetchBestRateWithinWindow(currency, purchaseDate, windowStart)
                .orElse(null);

        if (row == null) {
            log.warn("[TREASURY API] No rate returned for currency={} purchaseDate={} — cannot convert",
                    currency, purchaseDate);
            return null;
        }

        log.info("[TREASURY API] Rate received: currency={} effectiveDate={} recordDate={} rate={}",
                row.countryCurrencyDesc(), row.effectiveDate(), row.recordDate(), row.exchangeRate());

        LocalDate effectiveDate = TreasuryApiClient.parseDate(row.effectiveDate());
        BigDecimal rate = TreasuryApiClient.parseExchangeRate(row.exchangeRate())
                .setScale(6, RoundingMode.HALF_UP);
        ExchangeRate entity = exchangeRateRepository
                .findByCurrencyAndEffectiveDate(currency, effectiveDate)
                .orElseGet(() -> {
                    log.info("[DB SAVE] Persisting new rate: currency={} effectiveDate={} rate={}",
                            currency, effectiveDate, rate);
                    return exchangeRateRepository.save(ExchangeRate.builder()
                            .currency(currency)
                            .rate(rate)
                            .effectiveDate(effectiveDate)
                            .sourceTimestamp(Instant.now())
                            .build());
                });
        log.info("[CACHE STORE] Caching result for key={} effectiveDate={} rate={} id={}",
                cacheKey, entity.getEffectiveDate(), entity.getRate(), entity.getId());
        return entity;
    }

    private Optional<ExchangeRate> queryDb(String currency, LocalDate purchaseDate, LocalDate windowStart) {
        log.debug("[DB QUERY] Looking up rate: currency={} purchaseDate={} windowStart={}",
                currency, purchaseDate, windowStart);
        try {
            Optional<ExchangeRate> result =
                    exchangeRateRepository.findMostRecentInWindow(currency, purchaseDate, windowStart);
            if (result.isEmpty()) {
                log.debug("[DB MISS] No rate in DB for currency={} purchaseDate={}", currency, purchaseDate);
            }
            return result;
        } catch (Exception e) {
            log.error("[DB ERROR] Failed to query exchange rates for currency={} purchaseDate={}: {}",
                    currency, purchaseDate, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** Truncates a string for safe log output, appending an indicator when cut. */
    static String truncate(String value) {
        if (value == null) return "(null)";
        return value.length() <= LOG_PAYLOAD_MAX
                ? value
                : value.substring(0, LOG_PAYLOAD_MAX) + "... [truncated, total=" + value.length() + "]";
    }
}
