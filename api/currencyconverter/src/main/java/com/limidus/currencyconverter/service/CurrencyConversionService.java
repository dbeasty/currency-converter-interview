package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.domain.ConversionRecord;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.domain.Transaction;
import com.limidus.currencyconverter.dto.ConvertedTransactionResponse;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.repository.ConversionRepository;
import com.limidus.currencyconverter.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrencyConversionService {

    public static final String CONVERSION_UNAVAILABLE =
            "The purchase cannot be converted to the target currency.";

    private final TransactionService transactionService;
    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ConversionRepository conversionRepository;

    public CurrencyConversionService(
            TransactionService transactionService,
            ExchangeRateService exchangeRateService,
            ExchangeRateRepository exchangeRateRepository,
            ConversionRepository conversionRepository) {
        this.transactionService = transactionService;
        this.exchangeRateService = exchangeRateService;
        this.exchangeRateRepository = exchangeRateRepository;
        this.conversionRepository = conversionRepository;
    }

    @Transactional
    public ConvertedTransactionResponse getConvertedPurchase(UUID transactionId, String countryCurrencyDesc) {
        if (countryCurrencyDesc == null || countryCurrencyDesc.isBlank()) {
            throw new InvalidRequestException("Query parameter countryCurrencyDesc is required");
        }

        Transaction transaction = transactionService.getById(transactionId);

        var rateRow = exchangeRateService
                .findMostRecentRate(countryCurrencyDesc, transaction.getTransactionDate())
                .orElseThrow(() -> new InvalidRequestException(CONVERSION_UNAVAILABLE));

        LocalDate rateEffectiveDate = TreasuryApiClient.parseDate(rateRow.effectiveDate());
        BigDecimal exchangeRateUsed =
                TreasuryApiClient.parseExchangeRate(rateRow.exchangeRate()).setScale(6, RoundingMode.HALF_UP);

        ExchangeRate exchangeRateEntity = exchangeRateRepository
                .findByCurrencyAndEffectiveDate(countryCurrencyDesc, rateEffectiveDate)
                .orElseGet(() -> exchangeRateRepository.save(ExchangeRate.builder()
                        .currency(countryCurrencyDesc)
                        .rate(exchangeRateUsed)
                        .effectiveDate(rateEffectiveDate)
                        .sourceTimestamp(Instant.now())
                        .build()));

        BigDecimal convertedAmount = transaction
                .getAmountUsd()
                .multiply(exchangeRateUsed)
                .setScale(2, RoundingMode.HALF_UP);

        ConversionRecord record = ConversionRecord.builder()
                .transactionId(transaction.getId())
                .exchangeRateId(exchangeRateEntity.getId())
                .exchangeRateEffectiveDate(rateEffectiveDate)
                .exchangeRateUsed(exchangeRateUsed)
                .convertedAmount(convertedAmount)
                .conversionTimestamp(Instant.now())
                .build();
        conversionRepository.save(record);

        return ConvertedTransactionResponse.builder()
                .id(transaction.getId())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .purchaseAmountUsd(transaction.getAmountUsd())
                .countryCurrencyDesc(countryCurrencyDesc)
                .exchangeRateUsed(exchangeRateUsed)
                .exchangeRateDate(rateEffectiveDate)
                .exchangeRateAgeDays(ChronoUnit.DAYS.between(rateEffectiveDate, transaction.getTransactionDate()))
                .convertedAmount(convertedAmount)
                .build();
    }
}
