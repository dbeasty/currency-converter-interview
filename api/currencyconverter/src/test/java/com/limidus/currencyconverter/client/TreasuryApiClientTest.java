package com.limidus.currencyconverter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.limidus.currencyconverter.config.TreasuryProperties;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import java.time.LocalDate;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TreasuryApiClientTest {

    private MockWebServer server;
    private TreasuryApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        base = base.substring(0, base.length() - 1);
        TreasuryProperties props = new TreasuryProperties();
        props.setBaseUrl(base);
        RestClient restClient = RestClient.builder().build();
        client = new TreasuryApiClient(restClient, props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void fetchBestRateWithinWindow_returnsFirstRow() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(
                        "{\"data\":[{\"country_currency_desc\":\"Canada-Dollar\",\"exchange_rate\":\"1.5\",\"record_date\":\"2024-01-01\",\"effective_date\":\"2024-01-01\"}]}")
                .addHeader("Content-Type", "application/json"));

        var row = client.fetchBestRateWithinWindow(
                "Canada Dollar", LocalDate.of(2024, 6, 1), LocalDate.of(2024, 1, 1));

        assertThat(row).isPresent();
        assertThat(row.get().exchangeRate()).isEqualTo("1.5");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains("Canada");
        assertThat(req.getPath()).contains("Dollar");
    }

    @Test
    void fetchBestRateWithinWindow_emptyData_returnsEmpty() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchBestRateWithinWindow("X", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)))
                .isEmpty();
    }

    @Test
    void fetchBestRateWithinWindow_nullData_returnsEmpty() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":null}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchBestRateWithinWindow("X", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)))
                .isEmpty();
    }

    @Test
    void fetchBestRateWithinWindow_nullBody_returnsEmpty() {
        // Literal JSON null deserializes into a null TreasuryRatesResponse instance.
        server.enqueue(new MockResponse()
                .setBody("null")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchBestRateWithinWindow("X", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)))
                .isEmpty();
    }

    @Test
    void fetchBestRateWithinWindow_httpError_throwsInvalidRequest() {
        // Use 400 (not 503): Apache HttpClient retries 503 and MockWebServer only serves one response.
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));

        assertThatThrownBy(() -> client.fetchBestRateWithinWindow(
                        "Y", LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("400");
    }

    @Test
    void fetchAllRatesInWindow_returnsRows() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(
                        "{\"data\":[{\"country_currency_desc\":\"A\",\"exchange_rate\":\"1\",\"record_date\":\"2024-01-01\",\"effective_date\":\"2024-01-01\"}]}")
                .addHeader("Content-Type", "application/json"));

        List<TreasuryApiClient.TreasuryRateRow> rows =
                client.fetchAllRatesInWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1));

        assertThat(rows).hasSize(1);
        assertThat(server.takeRequest().getPath()).contains("page[size]=500");
    }

    @Test
    void fetchAllRatesInWindow_nullData_returnsEmptyList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":null}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchAllRatesInWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
                .isEmpty();
    }

    @Test
    void fetchAllRatesInWindow_emptyData_returnsEmptyList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchAllRatesInWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
                .isEmpty();
    }

    @Test
    void fetchAllRatesInWindow_nullBody_returnsEmptyList() {
        server.enqueue(new MockResponse()
                .setBody("null")
                .addHeader("Content-Type", "application/json"));

        assertThat(client.fetchAllRatesInWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
                .isEmpty();
    }

    @Test
    void fetchAllRatesInWindow_httpError_throwsInvalidRequest() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));

        assertThatThrownBy(() -> client.fetchAllRatesInWindow(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("bulk load");
    }

    @Test
    void parseExchangeRate_invalid_throws() {
        assertThatThrownBy(() -> TreasuryApiClient.parseExchangeRate("not-a-number"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void parseExchangeRate_zero_throws() {
        assertThatThrownBy(() -> TreasuryApiClient.parseExchangeRate("0"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void parseExchangeRate_negative_throws() {
        assertThatThrownBy(() -> TreasuryApiClient.parseExchangeRate("-1.5"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void parseExchangeRate_valid_returnsValue() {
        assertThat(TreasuryApiClient.parseExchangeRate("1.25"))
                .isEqualByComparingTo("1.25");
    }

    @Test
    void parseDate_invalid_throws() {
        assertThatThrownBy(() -> TreasuryApiClient.parseDate("2024-13-40"))
                .isInstanceOf(InvalidRequestException.class);
    }
}
