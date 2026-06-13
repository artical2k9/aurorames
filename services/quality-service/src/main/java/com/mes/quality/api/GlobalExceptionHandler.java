package com.mes.quality.api;

import com.mes.quality.service.QualityConflictException;
import com.mes.quality.service.QualityNotFoundException;
import com.mes.quality.service.QualityValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QualityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(QualityNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(QualityConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(QualityConflictException ex) {
        return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(QualityValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(QualityValidationException ex) {
        return ResponseEntity.status(422).body(Map.of(
                "error", ex.getMessage(),
                "details", ex.getDetails()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("error", "Data integrity violation"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBindingValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first != null ? first.getField() + ": " + first.getDefaultMessage() : "Validation failed";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
