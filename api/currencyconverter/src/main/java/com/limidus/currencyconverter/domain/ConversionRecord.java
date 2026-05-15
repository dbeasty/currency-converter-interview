package com.limidus.currencyconverter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversion_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /** FK to exchange_rates.id — stored as a plain UUID for auditing; no JPA join loaded. */
    @Column(name = "exchange_rate_id", nullable = false)
    private UUID exchangeRateId;

    /** Snapshot of the Treasury effective_date for the rate row applied at conversion time. */
    @Column(name = "exchange_rate_effective_date", nullable = false)
    private LocalDate exchangeRateEffectiveDate;

    /** Snapshot: the actual rate value applied at conversion time. */
    @Column(name = "exchange_rate_used", nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRateUsed;

    @Column(name = "converted_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal convertedAmount;

    @Column(name = "conversion_timestamp")
    private Instant conversionTimestamp;
}
