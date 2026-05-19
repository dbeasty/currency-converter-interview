package com.limidus.currencyconverter.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.exception.ExternalServiceException;

@Component
public class TreasuryApiClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryApiClient.class);
    private static final String RATES_PATH = "/v1/accounting/od/rates_of_exchange";
    private static final int BULK_PAGE_SIZE = 500;

    private final RestClient restClient;
    private final TreasuryProperties treasuryProperties;

    public TreasuryApiClient(RestClient restClient, TreasuryProperties treasuryProperties) {
        this.restClient = restClient;
        this.treasuryProperties = treasuryProperties;
    }

    /**
     * Returns the most recent Treasury rate row whose {@code effective_date} is on or before
     * {@code purchaseDate} and on or after {@code windowStartInclusive}.
     *
     * <p>We filter and sort by {@code effective_date} rather than {@code record_date} because
     * Treasury publishes mid-quarter amendments for volatile currencies (e.g. Argentina, Turkey).
     * A mid-quarter amendment keeps the original quarter-end {@code record_date} but carries a
     * later {@code effective_date} reflecting when that amended rate became authoritative. Sorting
     * by {@code -effective_date} ensures the amendment surfaces above the baseline rate for the
     * same quarter, so callers always receive the most current rate that was in effect on the
     * purchase date rather than an outdated baseline.
     */
    public Optional<TreasuryRateRow> fetchBestRateWithinWindow(
            String countryCurrencyDesc, LocalDate purchaseDate, LocalDate windowStartInclusive) {
        String filter = "country_currency_desc:eq:%s,effective_date:lte:%s,effective_date:gte:%s"
                .formatted(escapeDescriptorForFilter(countryCurrencyDesc), purchaseDate, windowStartInclusive);

        var uri = UriComponentsBuilder.fromUriString(treasuryProperties.getBaseUrl() + RATES_PATH)
                .queryParam("fields", "country_currency_desc,exchange_rate,record_date,effective_date")
                .queryParam("filter", filter)
                .queryParam("sort", "-effective_date")
                .queryParam("page[size]", 1)
                .build()
                .toUri();

        log.info("[TREASURY API] GET {}", uri);
        try {
            TreasuryRatesResponse body = restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(TreasuryRatesResponse.class);

            if (body == null || body.data() == null || body.data().isEmpty()) {
                log.info("[TREASURY API] Response: 0 rows returned for currency={} purchaseDate={}",
                        countryCurrencyDesc, purchaseDate);
                return Optional.empty();
            }
            TreasuryRateRow row = body.data().getFirst();
            log.info("[TREASURY API] Response: 1 row — currency={} effectiveDate={} recordDate={} rate={}",
                    row.countryCurrencyDesc(), row.effectiveDate(), row.recordDate(), row.exchangeRate());
            return Optional.of(row);
        } catch (RestClientResponseException e) {
            log.error("[TREASURY API] HTTP {} error for currency={} uri={}", e.getStatusCode().value(),
                    countryCurrencyDesc, uri);
            throw new ExternalServiceException(
                    "Treasury Fiscal Data API error: HTTP " + e.getStatusCode().value());
        }
    }

    /**
     * Fetches all currency exchange rates whose {@code effective_date} falls within the given
     * window. Used by the bulk loader to warm the database with every currency at once.
     *
     * <p>The Treasury API returns ~170 currencies per quarter, and there can be 2–3 quarters in a
     * 6-month window, so a page size of 500 is sufficient to retrieve all rows in a single call.
     * Paginates with {@code page[number]} until a page returns fewer than {@link #BULK_PAGE_SIZE} rows.
     */
    public List<TreasuryRateRow> fetchAllRatesInWindow(LocalDate windowStart, LocalDate windowEnd) {
        String filter = "effective_date:lte:%s,effective_date:gte:%s"
                .formatted(windowEnd, windowStart);

        List<TreasuryRateRow> all = new ArrayList<>();
        int page = 1;
        while (true) {
            var uri = UriComponentsBuilder.fromUriString(treasuryProperties.getBaseUrl() + RATES_PATH)
                    .queryParam("fields", "country_currency_desc,exchange_rate,record_date,effective_date")
                    .queryParam("filter", filter)
                    .queryParam("sort", "-effective_date")
                    .queryParam("page[size]", BULK_PAGE_SIZE)
                    .queryParam("page[number]", page)
                    .build()
                    .toUri();

            log.info("[TREASURY API][BULK] GET {} (page {})", uri, page);
            try {
                TreasuryRatesResponse body = restClient
                        .get()
                        .uri(uri)
                        .retrieve()
                        .body(TreasuryRatesResponse.class);

                if (body == null || body.data() == null || body.data().isEmpty()) {
                    if (page == 1) {
                        log.warn("[TREASURY API][BULK] Empty response for window {}/{}", windowStart, windowEnd);
                    }
                    break;
                }

                all.addAll(body.data());
                log.info("[TREASURY API][BULK] Page {}: {} rows (total so far: {})",
                        page, body.data().size(), all.size());

                if (body.data().size() < BULK_PAGE_SIZE) {
                    break;
                }
                page++;
            } catch (RestClientResponseException e) {
                log.error("[TREASURY API][BULK] HTTP {} error for window {}/{}: {}",
                        e.getStatusCode().value(), windowStart, windowEnd, e.getMessage());
                throw new ExternalServiceException(
                        "Treasury Fiscal Data API error during bulk load: HTTP " + e.getStatusCode().value());
            }
        }

        if (!all.isEmpty()) {
            log.debug("[TREASURY API][BULK] First row sample: {}", all.getFirst());
        }
        return all;
    }

    /** Encode spaces in descriptor so filter colons stay literal for the API parser. */
    static String escapeDescriptorForFilter(String descriptor) {
        return descriptor.replace(" ", "%20");
    }

    public static BigDecimal parseExchangeRate(String exchangeRate) {
        try {
            BigDecimal value = new BigDecimal(exchangeRate);
            if (value.signum() <= 0) {
                throw new ExternalServiceException(
                        "Exchange rate from Treasury API must be positive, got: " + exchangeRate);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ExternalServiceException("Invalid exchange_rate value from Treasury API");
        }
    }

    public static LocalDate parseDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException e) {
            throw new ExternalServiceException("Invalid date value from Treasury API: " + isoDate);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreasuryRatesResponse(List<TreasuryRateRow> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TreasuryRateRow(
            @JsonProperty("country_currency_desc") String countryCurrencyDesc,
            @JsonProperty("exchange_rate") String exchangeRate,
            @JsonProperty("record_date") String recordDate,
            @JsonProperty("effective_date") String effectiveDate) {}
}
