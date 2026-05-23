package com.mikemes.platform.unit.api;

import com.mikemes.platform.api.SystemConfigController;
import com.mikemes.platform.api.dto.SystemConfigDto;
import com.mikemes.platform.api.dto.UpsertConfigRequest;
import com.mikemes.platform.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigControllerTest {

    static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    SystemConfigService service;

    @InjectMocks
    SystemConfigController controller;

    private JwtAuthenticationToken jwtPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("org_id", ORG_ID.toString())
                .claim("sub", "user-123")
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }

    private SystemConfigDto dto(String key) {
        return new SystemConfigDto(1L, ORG_ID, key, "val", "desc", true, Instant.EPOCH, Instant.EPOCH, "user");
    }

    @Test
    void listAll_delegatesToService() {
        when(service.listAll(ORG_ID)).thenReturn(List.of(dto("k1"), dto("k2")));

        List<SystemConfigDto> result = controller.listAll(jwtPrincipal());

        assertThat(result).hasSize(2);
        verify(service).listAll(ORG_ID);
    }

    @Test
    void getByKey_found_returnsDto() {
        when(service.findByKey(ORG_ID, "k1")).thenReturn(Optional.of(dto("k1")));

        SystemConfigDto result = controller.getByKey("k1", jwtPrincipal());

        assertThat(result.key()).isEqualTo("k1");
    }

    @Test
    void getByKey_missing_throws404() {
        when(service.findByKey(ORG_ID, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getByKey("missing", jwtPrincipal()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void upsert_delegatesToService() {
        UpsertConfigRequest req = new UpsertConfigRequest("val", "desc");
        when(service.upsert(ORG_ID, "k1", req, "user-123")).thenReturn(dto("k1"));

        SystemConfigDto result = controller.upsert("k1", req, jwtPrincipal());

        assertThat(result.key()).isEqualTo("k1");
        verify(service).upsert(ORG_ID, "k1", req, "user-123");
    }

    @Test
    void softDelete_delegatesToService() {
        controller.softDelete("k1", jwtPrincipal());

        verify(service).softDelete(ORG_ID, "k1");
    }
}
