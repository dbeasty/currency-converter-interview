package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
}