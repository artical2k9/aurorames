package com.mikemes.auditservice.unit.api;

import com.mikemes.auditservice.api.AuditController;
import com.mikemes.auditservice.api.dto.AuthAuditRecordDto;
import com.mikemes.auditservice.config.SecurityConfig;
import com.mikemes.auditservice.service.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(roles = "AUDIT_READ")
    void getAuthEventsReturns200WithAuditReadRole() throws Exception {
        AuthAuditRecordDto dto = new AuthAuditRecordDto(
            UUID.randomUUID(), UUID.randomUUID(), "AUTH_EVENT",
            "user-test", "mes-frontend", "10.0.0.1",
            "session-xyz", "mikemes",
            OffsetDateTime.now(ZoneOffset.UTC), null
        );
        when(queryService.findAuthEvents(isNull(), isNull(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/audit/auth-events")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].eventType").value("AUTH_EVENT"))
            .andExpect(jsonPath("$.content[0].userId").value("user-test"));
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void getAuthEventsReturns200WithSystemAdminRole() throws Exception {
        when(queryService.findAuthEvents(isNull(), isNull(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/audit/auth-events")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_USER")
    void getAuthEventsReturns403WithoutAuditReadRole() throws Exception {
        mockMvc.perform(get("/audit/auth-events")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
