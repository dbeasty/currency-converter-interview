package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.ExchangeRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findByCurrencyAndEffectiveDate(String currency, LocalDate effectiveDate);

    /**
     * Returns the most recently effective rate for {@code currency} whose effective date is on or
     * before {@code purchaseDate} and on or after {@code windowStart}. Mirrors the filter+sort
     * logic used against the Treasury API so DB hits and API hits return consistent results.
     */
    @Query("SELECT e FROM ExchangeRate e WHERE e.currency = :currency " +
           "AND e.effectiveDate <= :purchaseDate AND e.effectiveDate >= :windowStart " +
           "ORDER BY e.effectiveDate DESC LIMIT 1")
    Optional<ExchangeRate> findMostRecentInWindow(
            @Param("currency") String currency,
            @Param("purchaseDate") LocalDate purchaseDate,
            @Param("windowStart") LocalDate windowStart);

    /**
     * Used by the bulk loader to check which (currency, effectiveDate) pairs are already stored
     * so upsert logic can skip re-saving unchanged rows.
     */
    @Query("SELECT e FROM ExchangeRate e WHERE e.effectiveDate >= :windowStart ORDER BY e.currency, e.effectiveDate")
    List<ExchangeRate> findAllInWindow(@Param("windowStart") LocalDate windowStart);
}
