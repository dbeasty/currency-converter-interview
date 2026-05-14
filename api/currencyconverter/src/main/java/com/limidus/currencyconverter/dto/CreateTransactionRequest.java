package com.limidus.currencyconverter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    @NotBlank
    @Size(max = 50)
    private String description;

    @NotNull
    private LocalDate transactionDate;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal purchaseAmountUsd;
}
