package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.domain.MonthYear;
import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

        LocalDate start = monthYear.rangeStart();
        LocalDate endExclusive = monthYear.rangeEndExclusive();

        long transactionCount = transactionRepository.countByTransactionDateInRange(start, endExclusive);
        BigDecimal totalUsd = transactionRepository.sumAmountUsdByTransactionDateInRange(start, endExclusive);
        if (totalUsd == null) {
            totalUsd = BigDecimal.ZERO;
        }

        return MonthlyReportResponse.builder()
                .month(monthYear.formatted())
                .transactionCount(transactionCount)
                .totalPurchaseAmountUsd(totalUsd)
                .build();
    }
}
