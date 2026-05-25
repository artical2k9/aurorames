package com.mes.iam.config;

import com.mes.common.security.annotation.EnableMESSecurity;
import com.mes.iam.filter.WebhookTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMESSecurity
public class SecurityConfig {

    /**
     * Higher-priority chain for /internal/** — authenticated by a static bearer token
     * (Docker secret) rather than a Keycloak JWT. Never exposed through the gateway.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            @Value("${iam.webhook.token}") String webhookToken) throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new WebhookTokenFilter(webhookToken),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }
}
