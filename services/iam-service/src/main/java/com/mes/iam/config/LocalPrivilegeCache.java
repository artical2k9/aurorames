package com.mes.iam.config;

import com.mes.common.security.privilege.PrivilegeCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * iam-service provides its own PrivilegeCache backed by the local DB, bypassing
 * the HTTP-based CaffeinePrivilegeCache from lib-common-security (which would
 * create a circular self-call). @ConditionalOnMissingBean in the auto-configuration
 * means this bean takes precedence.
 *
 * Uses JdbcTemplate with native SQL rather than JPQL via RolePrivilegeRepository
 * because this method runs in the Spring Security filter chain — before
 * OpenEntityManagerInViewInterceptor binds an EntityManager. With JPQL + lazy
 * @ManyToOne, calling a.getRole().getName() after findAllActive() returns would
 * require the Hibernate session to still be open, which is unreliable in that
 * pre-OEMIV context. Native SQL returns scalar strings directly with no lazy
 * loading and no Hibernate/Envers involvement.
 */
@Component
public class LocalPrivilegeCache implements PrivilegeCache {

    private static final String PRIVILEGES_FOR_ROLE_SQL =
            "SELECT p.privilege_key"
            + " FROM iam.role_privilege rp"
            + " JOIN iam.role r ON r.id = rp.role_id"
            + " JOIN iam.privilege p ON p.id = rp.privilege_id"
            + " WHERE rp.revoked_at IS NULL"
            + " AND r.name = ?";

    private final JdbcTemplate jdbcTemplate;

    public LocalPrivilegeCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> getPrivilegesForRole(String roleName) {
        List<String> keys = jdbcTemplate.queryForList(PRIVILEGES_FOR_ROLE_SQL, String.class, roleName);
        return new LinkedHashSet<>(keys);
    }
}
