package com.mes.auditservice.unit.api;

import com.mes.auditservice.api.GlobalExceptionHandler;
import com.mes.auditservice.api.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void handleTypeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("from");

        ErrorResponse response = handler.handleTypeMismatch(ex);

        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.message()).contains("from");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void handleConstraintViolationReturns400() {
        ConstraintViolationException ex = new ConstraintViolationException("must not be null", Set.of());

        ErrorResponse response = handler.handleConstraintViolation(ex);

        assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.message()).isEqualTo("must not be null");
    }

    @Test
    void handleAccessDeniedReturns403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ErrorResponse response = handler.handleAccessDenied(ex);

        assertThat(response.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.error()).isEqualTo("Forbidden");
    }
}
