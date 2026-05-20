package com.mikemes.iam.api;

import com.mikemes.iam.api.dto.ErrorResponse;
import com.mikemes.iam.exception.ActiveRoleAssignmentsException;
import com.mikemes.iam.exception.DuplicateRoleNameException;
import com.mikemes.iam.exception.PrivilegeNotFoundException;
import com.mikemes.iam.exception.RoleNotFoundException;
import com.mikemes.iam.exception.SystemRoleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFound(RoleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("not_found", ex.getMessage()));
    }

    @ExceptionHandler(PrivilegeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePrivilegeNotFound(PrivilegeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("not_found", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateRoleNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateRoleNameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("conflict", ex.getMessage()));
    }

    @ExceptionHandler(SystemRoleException.class)
    public ResponseEntity<ErrorResponse> handleSystemRole(SystemRoleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("bad_request", ex.getMessage()));
    }

    @ExceptionHandler(ActiveRoleAssignmentsException.class)
    public ResponseEntity<ErrorResponse> handleActiveAssignments(ActiveRoleAssignmentsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("conflict", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation_error", message));
    }
}
