package com.mikemes.audit.autoconfigure;

import com.mikemes.audit.checksum.ChecksumService;
import com.mikemes.audit.envers.MesRevisionListener;
import org.hibernate.envers.strategy.ValidityAuditStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfiguration
public class AuditAutoConfiguration {

    @Bean
    public MesRevisionListener mesRevisionListener() {
        return new MesRevisionListener();
    }

    @Bean
    public ChecksumService checksumService() {
        return new ChecksumService();
    }

    @Bean
    public HibernatePropertiesCustomizer enversAuditStrategyCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put(
                "org.hibernate.envers.audit_strategy",
                ValidityAuditStrategy.class.getName()
            );
            hibernateProperties.put(
                "org.hibernate.envers.audit_strategy_validity_end_rev_field_name",
                "REVEND"
            );
            hibernateProperties.put(
                "org.hibernate.envers.audit_strategy_validity_revend_timestamp_field_name",
                "REVEND_TSTMP"
            );
            hibernateProperties.put(
                "org.hibernate.envers.audit_strategy_validity_store_revend_timestamp",
                "true"
            );
            hibernateProperties.put(
                "org.hibernate.envers.store_data_at_delete",
                "true"
            );
        };
    }
}
