package com.limidus.currencyconverter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void getPurchaseTotals_queriesInclusiveMonthRange() {
        Clock clock = Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneId.of("America/New_York"));
        MonthlyReportService monthlyReportService = new MonthlyReportService(transactionRepository, clock);

        when(transactionRepository.countByTransactionDateInRange(
                        eq(LocalDate.of(2024, 6, 1)), eq(LocalDate.of(2024, 7, 1))))
                .thenReturn(2L);
        when(transactionRepository.sumAmountUsdByTransactionDateInRange(
                        eq(LocalDate.of(2024, 6, 1)), eq(LocalDate.of(2024, 7, 1))))
                .thenReturn(new BigDecimal("75.50"));

        MonthlyReportResponse response = monthlyReportService.getPurchaseTotals("06-2024");

        assertThat(response.getMonth()).isEqualTo("06-2024");
        assertThat(response.getTransactionCount()).isEqualTo(2);
        assertThat(response.getTotalPurchaseAmountUsd()).isEqualByComparingTo("75.50");
        verify(transactionRepository)
                .countByTransactionDateInRange(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 7, 1));
        verify(transactionRepository)
                .sumAmountUsdByTransactionDateInRange(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 7, 1));
    }
}
