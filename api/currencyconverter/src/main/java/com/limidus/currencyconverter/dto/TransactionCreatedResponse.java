package com.limidus.currencyconverter.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCreatedResponse {

    private UUID id;
    private String description;

    private LocalDate transactionDate;

    private BigDecimal purchaseAmountUsd;

    private LocalDateTime createdAt;
}
