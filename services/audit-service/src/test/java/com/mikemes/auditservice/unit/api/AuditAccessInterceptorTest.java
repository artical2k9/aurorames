package com.mikemes.auditservice.unit.api;

import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.auditservice.api.AuditAccessInterceptor;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAccessInterceptorTest {

    @Mock
    private AuditRecordRepository repository;

    @InjectMocks
    private AuditAccessInterceptor interceptor;

    @Test
    void afterCompletionSavesAuditAccessRecord() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/audit/entities/WorkOrder/WO-001/history");
        var authority = new SimpleGrantedAuthority("ROLE_AUDIT_READ");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user:test", null, List.of(authority))
        );

        interceptor.afterCompletion(request, response, new Object(), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());
        AuditRecord saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("AUDIT_ACCESS");
        assertThat(saved.getEntityId()).isEqualTo("/audit/entities/WorkOrder/WO-001/history");
        assertThat(saved.getUserId()).isEqualTo("user:test");
        assertThat(saved.getAction()).isEqualTo("READ");

        SecurityContextHolder.clearContext();
    }

    @Test
    void afterCompletionUsesAnonymousWhenNoAuth() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/audit/entities");
        SecurityContextHolder.clearContext();

        interceptor.afterCompletion(request, response, new Object(), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("anonymous");
    }

    @Test
    void afterCompletionDoesNotThrowWhenRepositoryFails() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/audit/entities");
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));
        SecurityContextHolder.clearContext();

        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
