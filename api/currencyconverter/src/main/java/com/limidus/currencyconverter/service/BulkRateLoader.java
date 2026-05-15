package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional bulk replenishment of the {@code exchange_rates} table.
 *
 * <p>When {@code app.treasury.bulk-load-enabled=true}:
 * <ul>
 *   <li>Runs once on startup (after the application context is fully ready).
 *   <li>Runs daily at 09:30 AM Eastern Time — after Treasury typically publishes updated rates.
 * </ul>
 *
 * <p>Each run fetches all ~170 currencies for the current 6-month window in a single Treasury API
 * call. Both quarterly baselines and mid-quarter amendments are returned because we filter by
 * {@code effective_date} (not {@code record_date}). Each row is upserted: new rows are inserted,
 * and rows whose {@code effective_date} already exists in the DB are updated with the latest rate
 * in case Treasury issued a correction.
 *
 * <p>After the bulk load, the {@link ExchangeRateService} 3-tier lookup will find rates in the DB
 * for virtually every request, bypassing the Treasury API entirely until the next amendment.
 */
@Component
public class BulkRateLoader {

    private static final Logger log = LoggerFactory.getLogger(BulkRateLoader.class);

    private final TreasuryApiClient treasuryApiClient;
    private final ExchangeRateRepository exchangeRateRepository;
    private final TreasuryProperties treasuryProperties;
    private final ZoneId treasuryZone;

    public BulkRateLoader(
            TreasuryApiClient treasuryApiClient,
            ExchangeRateRepository exchangeRateRepository,
            TreasuryProperties treasuryProperties) {
        this.treasuryApiClient = treasuryApiClient;
        this.exchangeRateRepository = exchangeRateRepository;
        this.treasuryProperties = treasuryProperties;
        this.treasuryZone = ZoneId.of(treasuryProperties.getTimezone());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (treasuryProperties.isBulkLoadEnabled()) {
            log.info("Bulk rate loader: running initial load on startup");
            load();
        }
    }

    /**
     * Runs on the schedule defined by {@code app.treasury.bulk-load-cron}, evaluated in
     * {@code app.treasury.timezone}. Defaults to 09:30 ET daily — after Treasury typically
     * publishes new data. Override to run multiple times per day to catch afternoon amendments
     * (e.g. {@code "0 30 9,15 * * *"} for 09:30 and 15:30 ET).
     */
    @Scheduled(
            cron = "${app.treasury.bulk-load-cron:0 30 9 * * *}",
            zone = "${app.treasury.timezone:America/New_York}")
    public void onSchedule() {
        if (treasuryProperties.isBulkLoadEnabled()) {
            log.info("Bulk rate loader: running scheduled daily replenishment");
            load();
        }
    }

    @Transactional
    public void load() {
        LocalDate today = LocalDate.now(treasuryZone);
        LocalDate windowStart = today.minusMonths(6);

        log.info("Bulk rate loader: fetching all rates from {} to {}", windowStart, today);
        List<TreasuryRateRow> rows = treasuryApiClient.fetchAllRatesInWindow(windowStart, today);
        log.info("Bulk rate loader: received {} rows from Treasury", rows.size());

        if (rows.isEmpty()) {
            return;
        }

        // Build a lookup of already-stored rates keyed by (currency, effectiveDate)
        Map<String, ExchangeRate> existing = exchangeRateRepository.findAllInWindow(windowStart)
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getCurrency() + "|" + e.getEffectiveDate(),
                        e -> e));

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (TreasuryRateRow row : rows) {
            LocalDate effectiveDate;
            BigDecimal rate;
            try {
                effectiveDate = TreasuryApiClient.parseDate(row.effectiveDate());
                rate = TreasuryApiClient.parseExchangeRate(row.exchangeRate())
                        .setScale(6, RoundingMode.HALF_UP);
            } catch (Exception e) {
                log.warn("Bulk rate loader: skipping invalid row currency={} rate={} date={}: {}",
                        row.countryCurrencyDesc(), row.exchangeRate(), row.effectiveDate(), e.getMessage());
                skipped++;
                continue;
            }
            String key = row.countryCurrencyDesc() + "|" + effectiveDate;

            ExchangeRate existing_ = existing.get(key);
            if (existing_ == null) {
                exchangeRateRepository.save(ExchangeRate.builder()
                        .currency(row.countryCurrencyDesc())
                        .rate(rate)
                        .effectiveDate(effectiveDate)
                        .sourceTimestamp(Instant.now())
                        .build());
                inserted++;
            } else if (existing_.getRate().compareTo(rate) != 0) {
                // Treasury issued a rate correction for an existing effective_date
                existing_.setRate(rate);
                existing_.setSourceTimestamp(Instant.now());
                exchangeRateRepository.save(existing_);
                updated++;
            }
        }

        log.info("Bulk rate loader: inserted={}, updated={}, unchanged={}, skipped={}", inserted, updated,
                rows.size() - inserted - updated - skipped, skipped);
    }
}
