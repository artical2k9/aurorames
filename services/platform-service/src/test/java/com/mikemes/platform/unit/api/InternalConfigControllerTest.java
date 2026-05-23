package com.mikemes.platform.unit.api;

import com.mikemes.platform.api.InternalConfigController;
import com.mikemes.platform.api.dto.SystemConfigDto;
import com.mikemes.platform.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalConfigControllerTest {

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    SystemConfigService service;

    @InjectMocks
    InternalConfigController controller;

    private SystemConfigDto dto(String key) {
        return new SystemConfigDto(1L, ORG_ID, key, "v", "d", true, Instant.EPOCH, Instant.EPOCH, "u");
    }

    @Test
    void getByKey_found_returnsDto() {
        when(service.findByKey(ORG_ID, "k")).thenReturn(Optional.of(dto("k")));

        assertThat(controller.getByKey("k", ORG_ID).key()).isEqualTo("k");
    }

    @Test
    void getByKey_missing_throws404() {
        when(service.findByKey(ORG_ID, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getByKey("nope", ORG_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
