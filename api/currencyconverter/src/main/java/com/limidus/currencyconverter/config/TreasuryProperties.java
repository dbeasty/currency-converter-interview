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

    /**
     * When true, all exchange rates for the current 6-month window are fetched from the Treasury
     * API on startup and refreshed on the schedule defined by {@code bulk-load-cron}. Keeps the
     * database warm so per-request Treasury API calls rarely fire. Disable in environments without
     * reliable outbound internet access or when low startup latency is required.
     */
    private boolean bulkLoadEnabled = false;

    /**
     * Cron expression controlling how often the bulk rate loader refreshes the database.
     * The timezone used to evaluate the expression is {@code app.treasury.timezone} (ET by default).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "0 30 9 * * *"} — once daily at 09:30 (default, aligns with Treasury's morning publish)
     *   <li>{@code "0 30 9,15 * * *"} — twice daily at 09:30 and 15:30 (catches afternoon amendments)
     *   <li>{@code "0 0/30 9-17 * * MON-FRI"} — every 30 min during Treasury business hours on weekdays
     * </ul>
     */
    private String bulkLoadCron = "0 30 9 * * *";

    /**
     * When true, the in-process Caffeine cache ({@code treasuryRates}) is bypassed for exchange-rate
     * lookups: every call runs the full lookup body (DB tier unless also disabled, then Treasury).
     * Intended for local debugging and freshness validation; do not enable in production under load.
     */
    private boolean inProcessCacheDisabled = false;

    /**
     * When true, the database read tier is skipped for exchange-rate lookups; the Treasury API is
     * called every time (subject to the 6-month window). Rows returned from Treasury are still
     * persisted. Intended for debugging stale DB hits; do not enable in production under load.
     */
    private boolean dbLookupDisabled = false;

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

    public boolean isBulkLoadEnabled() {
        return bulkLoadEnabled;
    }

    public void setBulkLoadEnabled(boolean bulkLoadEnabled) {
        this.bulkLoadEnabled = bulkLoadEnabled;
    }

    public String getBulkLoadCron() {
        return bulkLoadCron;
    }

    public void setBulkLoadCron(String bulkLoadCron) {
        this.bulkLoadCron = bulkLoadCron;
    }

    public boolean isInProcessCacheDisabled() {
        return inProcessCacheDisabled;
    }

    public void setInProcessCacheDisabled(boolean inProcessCacheDisabled) {
        this.inProcessCacheDisabled = inProcessCacheDisabled;
    }

    public boolean isDbLookupDisabled() {
        return dbLookupDisabled;
    }

    public void setDbLookupDisabled(boolean dbLookupDisabled) {
        this.dbLookupDisabled = dbLookupDisabled;
    }
}
