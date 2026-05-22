package com.mikemes.common.security.config;

import com.mikemes.common.security.auth.MikeMESJwtAuthenticationConverter;
import com.mikemes.common.security.privilege.CaffeinePrivilegeCache;
import com.mikemes.common.security.privilege.PrivilegeCache;
import com.mikemes.common.security.privilege.PrivilegeRegistryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
@EnableMethodSecurity
public class MikeMESSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    static AnnotationTemplateExpressionDefaults methodSecurityDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivilegeRegistryClient privilegeRegistryClient(
            RestClient.Builder builder,
            @Value("${mikemes.security.iam-service-url:http://localhost:8085}") String iamServiceUrl) {
        return new PrivilegeRegistryClient(builder, iamServiceUrl);
    }

    @Bean
    @ConditionalOnMissingBean(PrivilegeCache.class)
    public CaffeinePrivilegeCache caffeinePrivilegeCache(
            PrivilegeRegistryClient registryClient,
            @Value("${mikemes.security.privilege-cache-ttl-seconds:60}") long ttlSeconds) {
        return new CaffeinePrivilegeCache(registryClient, ttlSeconds);
    }

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
