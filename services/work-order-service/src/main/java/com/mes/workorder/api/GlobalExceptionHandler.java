package com.mes.workorder.api;

import com.mes.workorder.itemmaster.service.ItemMasterConflictException;
import com.mes.workorder.itemmaster.service.ItemMasterNotFoundException;
import com.mes.workorder.itemmaster.service.ItemMasterValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(String error, String message) {}

    @ExceptionHandler(ItemMasterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ItemMasterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("not_found", ex.getMessage()));
    }

    @ExceptionHandler(ItemMasterConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ItemMasterConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("conflict", ex.getMessage()));
    }

    @ExceptionHandler(ItemMasterValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ItemMasterValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("validation_error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBindingValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation_error", message));
    }
}
