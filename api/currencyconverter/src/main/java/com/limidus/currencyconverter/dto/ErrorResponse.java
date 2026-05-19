package com.limidus.currencyconverter.dto;

import org.springframework.http.HttpStatus;

public record ErrorResponse(int status, String error, String message) {

    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return new ErrorResponse(httpStatus.value(), httpStatus.getReasonPhrase(), message);
    }
}
