package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.domain.MonthYear;
import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportService {

    private final TransactionRepository transactionRepository;
    private final Clock businessClock;

    public MonthlyReportService(TransactionRepository transactionRepository, Clock businessClock) {
        this.transactionRepository = transactionRepository;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse getPurchaseTotals(String monthParam) {
        MonthYear monthYear = MonthYear.parse(monthParam);
        validateNotInFuture(monthYear);

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

    private void validateNotInFuture(MonthYear monthYear) {
        YearMonth currentBusinessMonth = YearMonth.now(businessClock);
        if (monthYear.yearMonth().isAfter(currentBusinessMonth)) {
            throw new InvalidRequestException("month cannot be in the future");
        }
    }
}
