package com.limidus.currencyconverter.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportResponse {

    private String month;
    private long transactionCount;
    private BigDecimal totalPurchaseAmountUsd;
}
