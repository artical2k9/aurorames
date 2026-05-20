package com.mikemes.common.security.config;

import com.mikemes.common.security.auth.MikeMESJwtAuthenticationConverter;
import com.mikemes.common.security.privilege.PrivilegeCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class MikeMESSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean(PrivilegeCache.class)
    public MikeMESJwtAuthenticationConverter mikeMESJwtAuthenticationConverter(PrivilegeCache privilegeCache) {
        return new MikeMESJwtAuthenticationConverter(privilegeCache);
    }

    @Bean
    @ConditionalOnBean(MikeMESJwtAuthenticationConverter.class)
    public SecurityFilterChain mikeMESSecurityFilterChain(
            HttpSecurity http,
            MikeMESJwtAuthenticationConverter converter) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }
}
