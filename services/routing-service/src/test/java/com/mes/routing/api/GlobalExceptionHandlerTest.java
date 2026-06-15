package com.mes.routing.api;

import com.mes.routing.service.RoutingConflictException;
import com.mes.routing.service.RoutingNotFoundException;
import com.mes.routing.service.RoutingValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFound_maps404() {
        ResponseEntity<Map<String, String>> r = handler.handleNotFound(new RoutingNotFoundException("nope"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsEntry("error", "nope");
    }

    @Test
    void conflict_maps409() {
        ResponseEntity<Map<String, String>> r = handler.handleConflict(new RoutingConflictException("dup"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "dup");
    }

    @Test
    void validation_maps422() {
        ResponseEntity<Map<String, String>> r = handler.handleValidation(new RoutingValidationException("bad"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).containsEntry("error", "bad");
    }

    @Test
    void dataIntegrity_maps409() {
        ResponseEntity<Map<String, String>> r =
                handler.handleDataIntegrity(new DataIntegrityViolationException("x"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void illegalArgument_maps400() {
        ResponseEntity<Map<String, String>> r =
                handler.handleIllegalArgument(new IllegalArgumentException("bad arg"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("error", "bad arg");
    }
}
