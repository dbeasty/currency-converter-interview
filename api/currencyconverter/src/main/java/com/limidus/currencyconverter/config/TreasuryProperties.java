package com.limidus.currencyconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.treasury")
public class TreasuryProperties {

    private String baseUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
