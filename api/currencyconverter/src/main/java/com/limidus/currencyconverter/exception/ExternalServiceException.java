package com.limidus.currencyconverter.exception;

/** Treasury API or other upstream dependency failure. */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) {
        super(message);
    }
}
