package com.footbooking.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(Instant timestamp, int status, String error, Map<String, String> details) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Donnees invalides", fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Conflit de donnees (doublon ou creneau deja pris)", Map.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, Map<String, String> details) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), message, details));
    }
}
