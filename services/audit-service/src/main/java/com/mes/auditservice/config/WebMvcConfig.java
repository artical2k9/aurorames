package com.mes.auditservice.config;

import com.mes.auditservice.api.AuditAccessInterceptor;
import com.mes.auditservice.repository.AuditRecordRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditRecordRepository repository;

    public WebMvcConfig(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuditAccessInterceptor(repository))
            .addPathPatterns("/audit/**");
    }
}
