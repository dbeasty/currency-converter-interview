package com.limidus.currencyconverter.repository;

import java.math.BigDecimal;

public record PurchaseTotals(long transactionCount, BigDecimal totalPurchaseAmountUsd) {}
