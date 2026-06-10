package com.mes.inventory.config;

import com.mes.udf.service.UdfAffectedRecordsFinder;
import com.mes.udf.service.UdfUsageChecker;
import com.mes.udf.service.UdfValueNullifier;
import com.mes.inventory.itemmaster.repository.ItemRevisionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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

    @Bean
    public UdfUsageChecker udfUsageChecker(JdbcTemplate jdbcTemplate) {
        return (orgId, fieldKey) -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory.item_revision ir"
                    + " JOIN inventory.item i ON i.id = ir.item_id"
                    + " WHERE i.org_id = ? AND jsonb_exists(ir.custom_fields, ?)",
                    Long.class, orgId, fieldKey);
            return count != null ? count : 0L;
        };
    }

    @Bean
    public UdfValueNullifier udfValueNullifier(ItemRevisionRepository repository,
                                               UdfAffectedRecordsFinder finder) {
        return (orgId, fieldKey) -> finder.findAffectedIds(orgId, fieldKey).forEach(id ->
                repository.findById(id).ifPresent(revision -> {
                    if (revision.getCustomFields() != null) {
                        revision.getCustomFields().remove(fieldKey);
                        repository.save(revision);
                    }
                }));
    }

    @Bean
    public UdfAffectedRecordsFinder udfAffectedRecordsFinder(JdbcTemplate jdbcTemplate) {
        return (orgId, fieldKey) -> jdbcTemplate.queryForList(
                "SELECT ir.id FROM inventory.item_revision ir"
                + " JOIN inventory.item i ON i.id = ir.item_id"
                + " WHERE i.org_id = ? AND jsonb_exists(ir.custom_fields, ?)",
                UUID.class, orgId, fieldKey);
    }
}
