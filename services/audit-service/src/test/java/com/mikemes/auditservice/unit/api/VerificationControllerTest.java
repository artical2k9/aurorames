package com.mikemes.auditservice.unit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mikemes.auditservice.api.VerificationController;
import com.mikemes.auditservice.api.dto.VerificationResult;
import com.mikemes.auditservice.config.SecurityConfig;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import com.mikemes.auditservice.service.TamperVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerificationController.class)
@Import(SecurityConfig.class)
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TamperVerificationService verificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuditRecordRepository auditRecordRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void postVerifyReturns200WithPassResultForSystemAdmin() throws Exception {
        VerificationResult passResult = new VerificationResult(
            "PASS", 100L, List.of(), OffsetDateTime.now(ZoneOffset.UTC)
        );
        when(verificationService.verify(any(), any())).thenReturn(passResult);

        String body = MAPPER.writeValueAsString(Map.of(
            "from", "2026-01-01T00:00:00Z",
            "to", "2026-01-02T00:00:00Z"
        ));

        mockMvc.perform(post("/audit/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PASS"))
            .andExpect(jsonPath("$.recordsChecked").value(100));
    }

    @Test
    @WithMockUser(roles = "AUDIT_READ")
    void postVerifyReturns403ForAuditReadRole() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
            "from", "2026-01-01T00:00:00Z",
            "to", "2026-01-02T00:00:00Z"
        ));

        mockMvc.perform(post("/audit/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }
}
