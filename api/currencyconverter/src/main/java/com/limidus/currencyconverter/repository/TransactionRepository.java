package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.Transaction;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("""
            SELECT new com.limidus.currencyconverter.repository.PurchaseTotals(
                COUNT(t),
                COALESCE(SUM(t.amountUsd), 0)
            )
            FROM Transaction t
            WHERE t.transactionDate >= :start AND t.transactionDate < :endExclusive
            """)
    PurchaseTotals sumAndCountByTransactionDateRange(
            @Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);
}