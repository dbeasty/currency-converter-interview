package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}