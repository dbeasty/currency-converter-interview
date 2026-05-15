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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIntegrationTest {

    private static final Pattern ID_JSON = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TreasuryApiClient treasuryApiClient;

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Chair"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(50.0))
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id).param("countryCurrencyDesc", "Canada-Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(100.0))
                .andExpect(jsonPath("$.exchangeRateUsed").value(2.0))
                .andExpect(jsonPath("$.countryCurrencyDesc").value("Canada-Dollar"));
    }

    @Test
    void get_unknownTransaction_returns404() throws Exception {
        UUID random = UUID.randomUUID();
        mockMvc.perform(get("/transactions/{id}", random).param("countryCurrencyDesc", "Canada-Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    void get_whenTreasuryReturnsNoRow_returns400() throws Exception {
        when(treasuryApiClient.fetchBestRateWithinWindow(any(), any(), any())).thenReturn(Optional.empty());

        String body = "{\"description\":\"Mug\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":5.00}";

        MvcResult created = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id).param("countryCurrencyDesc", "Unknown-Currency"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CurrencyConversionService.CONVERSION_UNAVAILABLE));
    }

    @Test
    void post_invalidDescription_returns400() throws Exception {
        String longDesc = "x".repeat(51);
        String body = "{\"description\":\""
                + longDesc
                + "\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":1.00}";

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_futureDate_returns400() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String body = "{\"description\":\"Desk\",\"transactionDate\":\""
                + tomorrow
                + "\",\"purchaseAmountUsd\":10.00}";

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_negativeAmount_returns400() throws Exception {
        String body = "{\"description\":\"Lamp\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":-5.00}";

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void post_zeroAmount_returns400() throws Exception {
        String body = "{\"description\":\"Lamp\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":0.00}";

        mockMvc.perform(post("/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_missingCurrency_returns400() throws Exception {
        when(treasuryApiClient.fetchBestRateWithinWindow(any(), any(), any())).thenReturn(Optional.empty());

        String body = "{\"description\":\"Pen\",\"transactionDate\":\"2024-06-15\",\"purchaseAmountUsd\":2.00}";
        MvcResult created = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        UUID id = extractId(created.getResponse().getContentAsString());

        mockMvc.perform(get("/transactions/{id}", id).param("countryCurrencyDesc", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
