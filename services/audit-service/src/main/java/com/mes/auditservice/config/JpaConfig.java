package com.mes.auditservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
    "com.mes.auditservice.repository"
})
@EntityScan(basePackages = {
    "com.mes.audit.domain",
    "com.mes.audit.envers"
})
public class JpaConfig {
}
