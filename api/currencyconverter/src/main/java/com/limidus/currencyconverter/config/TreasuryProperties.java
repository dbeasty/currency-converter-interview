package com.limidus.currencyconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.treasury")
public class TreasuryProperties {

    private String baseUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service";

    /**
     * IANA timezone in which Treasury publishes its exchange-rate data. Used to align the
     * cache-key date with Treasury's publication calendar so a cache miss never occurs before
     * Treasury has actually published updated rates for the day. Defaults to America/New_York
     * (Eastern Time), which is Treasury's operational timezone.
     */
    private String timezone = "America/New_York";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
