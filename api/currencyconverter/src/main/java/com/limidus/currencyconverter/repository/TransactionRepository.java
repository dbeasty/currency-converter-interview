package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("""
            SELECT COUNT(t)
            FROM Transaction t
            WHERE t.transactionDate >= :start AND t.transactionDate < :endExclusive
            """)
    long countByTransactionDateInRange(
            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);

    @Query("""
            SELECT SUM(t.amountUsd)
            FROM Transaction t
            WHERE t.transactionDate >= :start AND t.transactionDate < :endExclusive
            """)
    BigDecimal sumAmountUsdByTransactionDateInRange(
            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);
}