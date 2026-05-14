package com.limidus.currencyconverter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Column(nullable = false, length = 255)
    private String currency;

    @Column(name = "exchange_rate_used", nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRateUsed;

    @Column(name = "converted_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal convertedAmount;

    @Column(name = "conversion_timestamp")
    private LocalDateTime conversionTimestamp;
}
