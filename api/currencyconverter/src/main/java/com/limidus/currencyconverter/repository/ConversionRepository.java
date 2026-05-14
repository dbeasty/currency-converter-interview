package com.limidus.currencyconverter.repository;

import com.limidus.currencyconverter.domain.ConversionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConversionRepository extends JpaRepository<ConversionRecord, UUID> {
}