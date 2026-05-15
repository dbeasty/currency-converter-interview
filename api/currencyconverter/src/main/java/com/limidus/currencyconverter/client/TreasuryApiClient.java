package com.limidus.currencyconverter.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.exception.InvalidRequestException;

@Component
public class TreasuryApiClient {

    private static final String RATES_PATH = "/v1/accounting/od/rates_of_exchange";

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

        try {
            TreasuryRatesResponse body = restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(TreasuryRatesResponse.class);

            if (body == null || body.data() == null || body.data().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(body.data().getFirst());
        } catch (RestClientResponseException e) {
            throw new InvalidRequestException(
                    "Treasury Fiscal Data API error: HTTP " + e.getStatusCode().value());
        }
    }

    /** Encode spaces in descriptor so filter colons stay literal for the API parser. */
    static String escapeDescriptorForFilter(String descriptor) {
        return descriptor.replace(" ", "%20");
    }

    public static BigDecimal parseExchangeRate(String exchangeRate) {
        try {
            return new BigDecimal(exchangeRate);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Invalid exchange_rate value from Treasury API");
        }
    }

    public static LocalDate parseDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Invalid date value from Treasury API: " + isoDate);
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
