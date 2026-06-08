package com.mes.workorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                var jwt = jwtAuth.getToken();
                String given = jwt.getClaimAsString("given_name");
                String family = jwt.getClaimAsString("family_name");
                if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
                    return Optional.of(given.trim() + " " + family.trim());
                }
                if (given != null && !given.isBlank()) {
                    return Optional.of(given.trim());
                }
                if (family != null && !family.isBlank()) {
                    return Optional.of(family.trim());
                }
                String username = jwt.getClaimAsString("preferred_username");
                if (username != null && !username.isBlank()) {
                    return Optional.of(username);
                }
            }
            var name = auth.getName();
            return Optional.of(name != null ? name : "system");
        };
    }
}
