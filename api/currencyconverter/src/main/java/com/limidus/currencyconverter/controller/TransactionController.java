package com.limidus.currencyconverter.controller;

import com.limidus.currencyconverter.dto.ConvertedTransactionResponse;
import com.limidus.currencyconverter.dto.CreateTransactionRequest;
import com.limidus.currencyconverter.dto.MonthlyReportResponse;
import com.limidus.currencyconverter.dto.TransactionCreatedResponse;
import com.limidus.currencyconverter.service.CurrencyConversionService;
import com.limidus.currencyconverter.service.MonthlyReportService;
import com.limidus.currencyconverter.service.TransactionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrencyConversionService currencyConversionService;
    private final MonthlyReportService monthlyReportService;

    public TransactionController(
            TransactionService transactionService,
            CurrencyConversionService currencyConversionService,
            MonthlyReportService monthlyReportService) {
        this.transactionService = transactionService;
        this.currencyConversionService = currencyConversionService;
        this.monthlyReportService = monthlyReportService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionCreatedResponse create(@Valid @RequestBody CreateTransactionRequest request) {
        return transactionService.create(request);
    }

    @GetMapping("/transactions/{id}")
    public ConvertedTransactionResponse getConverted(
            @PathVariable UUID id, @RequestParam String countryCurrencyDesc) {
        return currencyConversionService.getConvertedPurchase(id, countryCurrencyDesc);
    }

    @GetMapping("/monthly-report")
    public MonthlyReportResponse getMonthlyReport(@RequestParam String month) {
        return monthlyReportService.getPurchaseTotals(month);
    }

}
