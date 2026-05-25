package com.mikemes.iam.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("mikemes");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void runMigrations() {
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("iam")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        jdbc = new JdbcTemplate(flyway.getConfiguration().getDataSource());
    }

    @Test
    void v001_allTablesExist() {
        assertTableExists("organisation");
        assertTableExists("role");
        assertTableExists("privilege");
        assertTableExists("role_privilege");
    }

    @Test
    void v002_sixSystemRolesSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM iam.role WHERE is_system_role = true",
                Integer.class);
        assertThat(count).isEqualTo(6);
    }

    @Test
    void v002_systemRoleNames_matchExpected() {
        var names = jdbc.queryForList(
                "SELECT name FROM iam.role WHERE is_system_role = true ORDER BY name",
                String.class);
        assertThat(names).containsExactly(
                "SYSTEM_ADMIN", "ENGINEER", "OPERATOR", "PLANNER", "QUALITY_INSPECTOR", "VIEWER");
    }

    @Test
    void v003_iamModulePrivilegesSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM iam.privilege WHERE module_name = 'iam'",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void v003_adminRoleAssignedAllIamPrivileges() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM iam.role_privilege rp "
                        + "JOIN iam.role r ON rp.role_id = r.id "
                        + "JOIN iam.privilege p ON rp.privilege_id = p.id "
                        + "WHERE r.name = 'ADMIN' AND r.is_system_role = true "
                        + "AND p.module_name = 'iam'",
                Integer.class);
        assertThat(count).isEqualTo(4);
    }

    @Test
    void v005_platformModulePrivilegesSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM iam.privilege WHERE module_name = 'platform'",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void v005_adminRoleAssignedAllPlatformPrivileges() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM iam.role_privilege rp "
                        + "JOIN iam.role r ON rp.role_id = r.id "
                        + "JOIN iam.privilege p ON rp.privilege_id = p.id "
                        + "WHERE r.name = 'ADMIN' AND r.is_system_role = true "
                        + "AND p.module_name = 'platform'",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void v004_enversTablesExist() {
        assertTableExists("revinfo");
        assertTableExists("organisation_aud");
        assertTableExists("role_aud");
        assertTableExists("role_privilege_aud");
    }

    @Test
    void role_orgId_notNullConstraintEnforced() {
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO iam.role (name, is_system_role, created_by, updated_by)"
                        + " VALUES ('TEST_ROLE', false, 'test', 'test')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void rolePrivilege_orgId_notNullConstraintEnforced() {
        String roleId = jdbc.queryForObject(
                "SELECT id FROM iam.role WHERE name = 'ADMIN' LIMIT 1",
                String.class);
        String privId = jdbc.queryForObject(
                "SELECT id FROM iam.privilege WHERE privilege_key = 'iam:users:view'",
                String.class);
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO iam.role_privilege (role_id, privilege_id, granted_by)"
                        + " VALUES ('" + roleId + "', '" + privId + "', 'test')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private static void assertTableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'iam' AND table_name = ?",
                Integer.class, tableName);
        assertThat(count).as("Table iam.%s should exist", tableName).isEqualTo(1);
    }
}
