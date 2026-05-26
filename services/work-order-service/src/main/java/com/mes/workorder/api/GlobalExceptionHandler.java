package com.mes.workorder.api;

import com.mes.udf.service.UdfDefinitionConflictException;
import com.mes.udf.service.UdfValidationException;
import com.mes.workorder.itemmaster.service.ItemMasterConflictException;
import com.mes.workorder.itemmaster.service.ItemMasterNotFoundException;
import com.mes.workorder.itemmaster.service.ItemMasterValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message, Instant timestamp) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "bad_request",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ItemMasterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ItemMasterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "not_found",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ItemMasterConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ItemMasterConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "conflict",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(UdfDefinitionConflictException.class)
    public ResponseEntity<ErrorResponse> handleUdfConflict(UdfDefinitionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "conflict",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(UdfValidationException.class)
    public ResponseEntity<ErrorResponse> handleUdfValidation(UdfValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), "validation_error",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ItemMasterValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ItemMasterValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), "validation_error",
                        ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBindingValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "validation_error",
                        message, Instant.now()));
    }
}
