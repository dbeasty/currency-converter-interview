package com.limidus.currencyconverter.exception;

/** No qualifying exchange rate exists for the requested currency and transaction date. */
public class ConversionUnavailableException extends RuntimeException {
    public ConversionUnavailableException(String message) {
        super(message);
    }
}
