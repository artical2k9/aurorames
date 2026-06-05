package com.mes.engineering.unit.api;

import com.mes.engineering.api.GlobalExceptionHandler;
import com.mes.engineering.eco.service.EcoConflictException;
import com.mes.engineering.eco.service.EcoNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleEcoNotFound_returns404() {
        ResponseEntity<?> resp = handler.handleEcoNotFound(new EcoNotFoundException("ECO not found"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleEcoConflict_returns409() {
        ResponseEntity<?> resp = handler.handleEcoConflict(new EcoConflictException("not in DRAFT"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleDataIntegrity_returns409() {
        ResponseEntity<?> resp = handler.handleDataIntegrity(
                new DataIntegrityViolationException("constraint"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<?> resp = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleBindingValidation_returns400_withFieldError() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "title", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod(
                                "handleBindingValidation_returns400_withFieldError"), -1),
                bindingResult);
        ResponseEntity<?> resp = handler.handleBindingValidation(ex);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
    }
}
