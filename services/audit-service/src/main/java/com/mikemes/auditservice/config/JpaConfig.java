package com.mikemes.auditservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
    "com.mikemes.auditservice.repository"
})
@EntityScan(basePackages = {
    "com.mikemes.audit.domain",
    "com.mikemes.audit.envers"
})
public class JpaConfig {
}
