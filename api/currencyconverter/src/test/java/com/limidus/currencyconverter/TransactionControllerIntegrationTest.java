package com.limidus.currencyconverter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.service.CurrencyConversionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

    private static final Pattern ID_JSON = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN_JSON =
            Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock businessClock;

    @MockitoBean
    private TreasuryApiClient treasuryApiClient;

    private String bearerToken;

    @BeforeEach
    void obtainBearerToken() throws Exception {
        MvcResult auth = mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"default-client\",\"clientSecret\":\"change-me-secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String json = auth.getResponse().getContentAsString();
        Matcher m = ACCESS_TOKEN_JSON.matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("No accessToken in: " + json);
        }
        bearerToken = "Bearer " + m.group(1);
    }

    private static UUID extractId(String json) {
        Matcher m = ID_JSON.matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("No id in JSON: " + json);
        }
        return UUID.fromString(m.group(1));
    }

    @Test
    void postThenGet_convertedPurchase() throws Exception {
        when(treasuryApiClient.fetchBestRateWithinWindow(
                        eq("Canada-Dollar"), eq(LocalDate.of(2024, 6, 15)), eq(LocalDate.of(2023, 12, 15))))
                .thenReturn(Optional.of(new TreasuryRateRow("Canada-Dollar", "2", "2024-06-01", "2024-06-01")));

        String body = "{\"description\":\"Chair\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":50.00}";

        MvcResult created = mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Chair"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(50.0))
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("countryCurrencyDesc", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(100.0))
                .andExpect(jsonPath("$.exchangeRateUsed").value(2.0))
                .andExpect(jsonPath("$.countryCurrencyDesc").value("Canada-Dollar"));
    }

    @Test
    void get_unknownTransaction_returns404() throws Exception {
        UUID random = UUID.randomUUID();
        mockMvc.perform(get("/transactions/{id}", random)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("countryCurrencyDesc", "Canada-Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    void get_whenTreasuryReturnsNoRow_returns422() throws Exception {
        when(treasuryApiClient.fetchBestRateWithinWindow(any(), any(), any())).thenReturn(Optional.empty());

        String body = "{\"description\":\"Mug\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":5.00}";

        MvcResult created = mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("countryCurrencyDesc", "Unknown-Currency"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(CurrencyConversionService.CONVERSION_UNAVAILABLE));
    }

    @Test
    void post_withoutToken_returnsForbidden() throws Exception {
        String body = "{\"description\":\"Chair\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":50.00}";
        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void authToken_invalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"default-client\",\"clientSecret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_version_isPublic() throws Exception {
        mockMvc.perform(get("/version")).andExpect(status().isOk());
    }

    @Test
    void post_invalidDescription_returns400() throws Exception {
        String longDesc = "x".repeat(51);
        String body = "{\"description\":\""
                + longDesc
                + "\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":1.00}";

        mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_futureDate_returns400() throws Exception {
        String tomorrow = LocalDate.now(businessClock).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String body = "{\"description\":\"Desk\",\"transactionDate\":\""
                + tomorrow
                + "\",\"purchaseAmountUsd\":10.00}";

        mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_negativeAmount_returns400() throws Exception {
        String body = "{\"description\":\"Lamp\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":-5.00}";

        mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_zeroAmount_returns400() throws Exception {
        String body = "{\"description\":\"Lamp\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":0.00}";

        mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getMonthlyReport_returnsTotalsForMonth() throws Exception {
        createTransaction("Mar A", "2019-03-10", "50.00");
        createTransaction("Mar B", "2019-03-20", "25.50");
        createTransaction("Apr", "2019-04-01", "100.00");

        mockMvc.perform(get("/monthly-report")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("month", "03-2019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("03-2019"))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.totalPurchaseAmountUsd").value(75.5));
    }

    @Test
    void getMonthlyReport_emptyMonth_returnsZeros() throws Exception {
        mockMvc.perform(get("/monthly-report")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("month", "01-2020"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("01-2020"))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.totalPurchaseAmountUsd").value(0));
    }

    @Test
    void getMonthlyReport_invalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/monthly-report")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("month", "2024-06"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month must be MM-YYYY"));
    }

    @Test
    void getMonthlyReport_futureMonth_returns400() throws Exception {
        YearMonth future = YearMonth.now(businessClock).plusMonths(1);
        String month = "%02d-%04d".formatted(future.getMonthValue(), future.getYear());

        mockMvc.perform(get("/monthly-report")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("month", month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month cannot be in the future"));
    }

    @Test
    void getMonthlyReport_withoutToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/monthly-report").param("month", "06-2024")).andExpect(status().isForbidden());
    }

    private void createTransaction(String description, String transactionDate, String amount)
            throws Exception {
        String body = "{\"description\":\""
                + description
                + "\",\"transactionDate\":\""
                + transactionDate
                + "\",\"purchaseAmountUsd\":"
                + amount
                + "}";
        mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void get_missingCurrency_returns400() throws Exception {
        when(treasuryApiClient.fetchBestRateWithinWindow(any(), any(), any())).thenReturn(Optional.empty());

        String body = "{\"description\":\"Pen\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":2.00}";
        MvcResult created = mockMvc.perform(post("/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("countryCurrencyDesc", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @TestConfiguration
    static class FixedBusinessClockConfig {
        @Bean
        @Primary
        Clock businessClock() {
            return Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneId.of("America/New_York"));
        }
    }
}
