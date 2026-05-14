package com.limidus.currencyconverter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertedTransactionResponse {

    private UUID id;
    private String description;

    private LocalDate transactionDate;

    private BigDecimal purchaseAmountUsd;

    private String countryCurrencyDesc;

    private BigDecimal exchangeRateUsed;

    private BigDecimal convertedAmount;
}
