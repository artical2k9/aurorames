package com.mes.inventory.config;

import com.mes.udf.service.UdfAffectedRecordsFinder;
import com.mes.udf.service.UdfUsageChecker;
import com.mes.udf.service.UdfValueNullifier;
import com.mes.inventory.itemmaster.repository.ItemMasterRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

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

    @Bean
    public UdfUsageChecker udfUsageChecker(JdbcTemplate jdbcTemplate) {
        return (orgId, fieldKey) -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory.item_master"
                    + " WHERE org_id = ? AND jsonb_exists(custom_fields, ?)",
                    Long.class, orgId, fieldKey);
            return count != null ? count : 0L;
        };
    }

    @Bean
    public UdfValueNullifier udfValueNullifier(ItemMasterRepository repository,
                                               UdfAffectedRecordsFinder finder) {
        return (orgId, fieldKey) -> finder.findAffectedIds(orgId, fieldKey).forEach(id ->
                repository.findByOrgIdAndId(orgId, id).ifPresent(item -> {
                    if (item.getCustomFields() != null) {
                        item.getCustomFields().remove(fieldKey);
                        repository.save(item);
                    }
                }));
    }

    @Bean
    public UdfAffectedRecordsFinder udfAffectedRecordsFinder(JdbcTemplate jdbcTemplate) {
        return (orgId, fieldKey) -> jdbcTemplate.queryForList(
                "SELECT id FROM inventory.item_master"
                + " WHERE org_id = ? AND jsonb_exists(custom_fields, ?)",
                UUID.class, orgId, fieldKey);
    }
}
