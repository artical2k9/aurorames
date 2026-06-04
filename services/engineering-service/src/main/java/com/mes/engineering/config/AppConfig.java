package com.mes.engineering.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AppConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }
            var name = auth.getName();
            return Optional.of(name != null ? name : "system");
        };
    }
}
