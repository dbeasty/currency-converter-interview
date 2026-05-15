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

    /** Treasury record_date of the rate applied. */
    private LocalDate exchangeRateDate;

    /** How many days before the purchase date the rate record was published. */
    private long exchangeRateAgeDays;

    private BigDecimal convertedAmount;
}
