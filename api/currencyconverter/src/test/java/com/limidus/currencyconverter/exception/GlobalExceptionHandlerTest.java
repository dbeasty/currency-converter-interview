package com.limidus.currencyconverter.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.limidus.currencyconverter.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound() {
        ResponseEntity<ErrorResponse> res = handler.handleNotFound(new NotFoundException("missing"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().message()).isEqualTo("missing");
    }

    @Test
    void handleBadCredentials() {
        ResponseEntity<ErrorResponse> res =
                handler.handleBadCredentials(new BadCredentialsException("bad login"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().message()).isEqualTo("bad login");
    }

    @Test
    void handleInvalidRequest() {
        ResponseEntity<ErrorResponse> res =
                handler.handleInvalidRequest(new InvalidRequestException("nope"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).isEqualTo("nope");
    }

    @Test
    void handleValidation_usesFirstFieldError() throws Exception {
        var target = new Object();
        var binding = new BeanPropertyBindingResult(target, "target");
        binding.addError(new FieldError("target", "amount", "must be positive"));
        var ex = new MethodArgumentNotValidException(null, binding);
        ResponseEntity<ErrorResponse> res = handler.handleValidation(ex);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).isEqualTo("amount: must be positive");
    }

    @Test
    void handleUnexpected() {
        ResponseEntity<ErrorResponse> res = handler.handleUnexpected(new RuntimeException("boom"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
