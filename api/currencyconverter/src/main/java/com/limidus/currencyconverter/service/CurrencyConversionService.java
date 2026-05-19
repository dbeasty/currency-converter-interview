package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.domain.ConversionRecord;
import com.limidus.currencyconverter.domain.ExchangeRate;
import com.limidus.currencyconverter.domain.Transaction;
import com.limidus.currencyconverter.dto.ConvertedTransactionResponse;
import com.limidus.currencyconverter.exception.ConversionUnavailableException;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.repository.ConversionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
    private final ConversionRepository conversionRepository;

    public CurrencyConversionService(
            TransactionService transactionService,
            ExchangeRateService exchangeRateService,
            ConversionRepository conversionRepository) {
        this.transactionService = transactionService;
        this.exchangeRateService = exchangeRateService;
        this.conversionRepository = conversionRepository;
    }

    @Transactional
    public ConvertedTransactionResponse getConvertedPurchase(UUID transactionId, String countryCurrencyDesc) {
        if (countryCurrencyDesc == null || countryCurrencyDesc.isBlank()) {
            throw new InvalidRequestException("Query parameter countryCurrencyDesc is required");
        }

        Transaction transaction = transactionService.getById(transactionId);

        ExchangeRate exchangeRate = exchangeRateService
                .findMostRecentRate(countryCurrencyDesc, transaction.getTransactionDate())
                .orElseThrow(() -> new ConversionUnavailableException(CONVERSION_UNAVAILABLE));

        BigDecimal convertedAmount = transaction.getAmountUsd()
                .multiply(exchangeRate.getRate())
                .setScale(2, RoundingMode.HALF_UP);

        ConversionRecord record = ConversionRecord.builder()
                .transactionId(transaction.getId())
                .exchangeRateId(exchangeRate.getId())
                .exchangeRateEffectiveDate(exchangeRate.getEffectiveDate())
                .exchangeRateUsed(exchangeRate.getRate())
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
                .exchangeRateUsed(exchangeRate.getRate())
                .exchangeRateDate(exchangeRate.getEffectiveDate())
                .exchangeRateAgeDays(ChronoUnit.DAYS.between(exchangeRate.getEffectiveDate(), transaction.getTransactionDate()))
                .convertedAmount(convertedAmount)
                .build();
    }
}
