package com.mes.workorder.config;

import com.mes.common.security.annotation.EnableMESSecurity;
import com.mes.workorder.filter.WebhookTokenFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMESSecurity
@EnableConfigurationProperties(WorkOrderSecurityProperties.class)
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            WorkOrderSecurityProperties securityProperties) throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new WebhookTokenFilter(securityProperties.getWebhookToken()),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }
}
