package com.mes.inventory.unit.api;

import com.mes.inventory.api.GlobalExceptionHandler;
import com.mes.inventory.bom.service.BomConflictException;
import com.mes.inventory.bom.service.BomNotFoundException;
import com.mes.inventory.bom.service.BomValidationException;
import com.mes.inventory.bom.service.EffectivityOverlapException;
import com.mes.inventory.itemmaster.service.ItemMasterConflictException;
import com.mes.inventory.itemmaster.service.ItemMasterNotFoundException;
import com.mes.inventory.itemmaster.service.ItemMasterValidationException;
import com.mes.udf.service.UdfDefinitionConflictException;
import com.mes.udf.service.UdfFieldNotFoundException;
import com.mes.udf.service.UdfValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<?> resp = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleItemMasterNotFound_returns404() {
        ResponseEntity<?> resp = handler.handleNotFound(new ItemMasterNotFoundException("not found"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleUdfNotFound_returns404() {
        ResponseEntity<?> resp = handler.handleUdfNotFound(new UdfFieldNotFoundException("udf not found"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleDataIntegrity_returns409() {
        ResponseEntity<?> resp = handler.handleDataIntegrity(
                new DataIntegrityViolationException("constraint violation"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleItemMasterConflict_returns409() {
        ResponseEntity<?> resp = handler.handleConflict(new ItemMasterConflictException("conflict"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleUdfConflict_returns409() {
        ResponseEntity<?> resp = handler.handleUdfConflict(new UdfDefinitionConflictException("udf conflict"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleUdfValidation_returns422() {
        ResponseEntity<?> resp = handler.handleUdfValidation(new UdfValidationException("udf invalid"));
        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleItemMasterValidation_returns422() {
        ResponseEntity<?> resp = handler.handleValidation(new ItemMasterValidationException("invalid"));
        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleBomNotFound_returns404() {
        ResponseEntity<?> resp = handler.handleBomNotFound(new BomNotFoundException("bom not found"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleBomConflict_returns409() {
        ResponseEntity<?> resp = handler.handleBomConflict(new BomConflictException("bom conflict"));
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleBomValidation_returns422() {
        ResponseEntity<?> resp = handler.handleBomValidation(new BomValidationException("bom invalid"));
        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleEffectivityOverlap_returns422() {
        ResponseEntity<?> resp = handler.handleEffectivityOverlap(
                new EffectivityOverlapException("F-001", UUID.randomUUID()));
        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void handleBindingValidation_returns400_withFirstFieldError() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "partNumber", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(
                        GlobalExceptionHandlerTest.class.getDeclaredMethod(
                                "handleBindingValidation_returns400_withFirstFieldError"), -1),
                bindingResult);
        ResponseEntity<?> resp = handler.handleBindingValidation(ex);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
    }
}
