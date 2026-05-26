package com.mes.workorder.config;

import com.mes.udf.service.UdfAffectedRecordsFinder;
import com.mes.udf.service.UdfUsageChecker;
import com.mes.udf.service.UdfValueNullifier;
import com.mes.workorder.itemmaster.repository.ItemMasterRepository;
import com.mes.workorder.preferences.api.dto.ColumnPreferenceEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
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
            return Optional.of(auth.getName());
        };
    }

    @Bean
    public UdfUsageChecker udfUsageChecker(JdbcTemplate jdbcTemplate) {
        return (orgId, fieldKey) -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM work_order.item_master"
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
                "SELECT id FROM work_order.item_master"
                + " WHERE org_id = ? AND jsonb_exists(custom_fields, ?)",
                UUID.class, orgId, fieldKey);
    }

    @Bean
    public Map<String, List<ColumnPreferenceEntry>> defaultColumnRegistry() {
        return Map.of(
                "ITEM_MASTER", List.of(
                        new ColumnPreferenceEntry("partNumber", true, 0),
                        new ColumnPreferenceEntry("revision", true, 1),
                        new ColumnPreferenceEntry("description", true, 2),
                        new ColumnPreferenceEntry("classification", true, 3),
                        new ColumnPreferenceEntry("makeBuyCode", true, 4),
                        new ColumnPreferenceEntry("unitOfMeasure", true, 5),
                        new ColumnPreferenceEntry("status", true, 6),
                        new ColumnPreferenceEntry("counterfeitRiskLevel", false, 7),
                        new ColumnPreferenceEntry("cageCode", false, 8)
                )
        );
    }
}
