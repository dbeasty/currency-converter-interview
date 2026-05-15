package com.limidus.currencyconverter.service;

import com.limidus.currencyconverter.domain.Transaction;
import com.limidus.currencyconverter.dto.CreateTransactionRequest;
import com.limidus.currencyconverter.dto.TransactionCreatedResponse;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.exception.NotFoundException;
import com.limidus.currencyconverter.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionCreatedResponse create(CreateTransactionRequest request) {
        BigDecimal amountUsd = request.getPurchaseAmountUsd().setScale(2, RoundingMode.HALF_UP);
        if (amountUsd.compareTo(new BigDecimal("0.01")) < 0) {
            throw new InvalidRequestException("Purchase amount must be at least one cent after rounding");
        }

        Transaction entity = Transaction.builder()
                .description(request.getDescription())
                .transactionDate(request.getTransactionDate())
                .amountUsd(amountUsd)
                .createdAt(Instant.now())
                .build();

        Transaction saved = transactionRepository.save(entity);
        return toCreatedResponse(saved);
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    private static TransactionCreatedResponse toCreatedResponse(Transaction t) {
        return TransactionCreatedResponse.builder()
                .id(t.getId())
                .description(t.getDescription())
                .transactionDate(t.getTransactionDate())
                .purchaseAmountUsd(t.getAmountUsd())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
