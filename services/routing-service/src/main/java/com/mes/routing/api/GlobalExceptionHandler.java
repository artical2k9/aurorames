package com.mes.routing.api;

import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoutingNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RoutingNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RoutingConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(RoutingConflictException ex) {
        return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RoutingValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(RoutingValidationException ex) {
        return ResponseEntity.status(422).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(Map.of("error", "Data integrity violation"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleBindingValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first != null
                ? first.getField() + ": " + first.getDefaultMessage()
                : "Validation failed";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
