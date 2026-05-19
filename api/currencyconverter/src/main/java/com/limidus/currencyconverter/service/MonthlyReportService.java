package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.domain.MonthYear;
import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.repository.PurchaseTotals;
import com.limidus.currencyconverter.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportService {

    private final TransactionRepository transactionRepository;

    public MonthlyReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse getPurchaseTotals(String monthParam) {
        MonthYear monthYear = MonthYear.parse(monthParam);
        monthYear.validateNotInFuture();

        PurchaseTotals totals = transactionRepository.sumAndCountByTransactionDateRange(
                monthYear.rangeStart(), monthYear.rangeEndExclusive());

        return MonthlyReportResponse.builder()
                .month(monthYear.formatted())
                .transactionCount(totals.transactionCount())
                .totalPurchaseAmountUsd(totals.totalPurchaseAmountUsd())
                .build();
    }
}
