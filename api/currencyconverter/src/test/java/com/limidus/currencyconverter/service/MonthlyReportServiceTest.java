package com.limidus.currencyconverter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.repository.PurchaseTotals;
import com.limidus.currencyconverter.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private MonthlyReportService monthlyReportService;

    @Test
    void getPurchaseTotals_queriesInclusiveMonthRange() {
        when(transactionRepository.sumAndCountByTransactionDateRange(
                        eq(LocalDate.of(2024, 6, 1)), eq(LocalDate.of(2024, 7, 1))))
                .thenReturn(new PurchaseTotals(2, new BigDecimal("75.50")));

        MonthlyReportResponse response = monthlyReportService.getPurchaseTotals("06-2024");

        assertThat(response.getMonth()).isEqualTo("06-2024");
        assertThat(response.getTransactionCount()).isEqualTo(2);
        assertThat(response.getTotalPurchaseAmountUsd()).isEqualByComparingTo("75.50");
        verify(transactionRepository)
                .sumAndCountByTransactionDateRange(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 7, 1));
    }
}
