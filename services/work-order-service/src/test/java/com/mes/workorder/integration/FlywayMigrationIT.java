package com.mes.workorder.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends BaseIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void allWorkOrderTablesExist() throws SQLException {
        List<String> tables = queryWorkOrderTables();
        assertThat(tables).containsExactlyInAnyOrder(
                "item_master",
                "bill_of_materials",
                "bom_line",
                "engineering_change_order",
                "eco_affected_item",
                "udf_field_definition",
                "user_grid_preferences",
                "revinfo",
                "item_master_aud",
                "bill_of_materials_aud",
                "bom_line_aud",
                "engineering_change_order_aud",
                "udf_field_definition_aud"
        );
    }

    @Test
    void itemMasterPrivilegesSeeded() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'item-master'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(5);
        }
    }

    @Test
    void systemAdminHasAllItemMasterGrants() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM iam.role_privilege rp" +
                    " JOIN iam.role r ON r.id = rp.role_id" +
                    " JOIN iam.privilege p ON p.id = rp.privilege_id" +
                    " WHERE r.name = 'SYSTEM_ADMIN' AND p.module_name = 'item-master'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(5);
        }
    }

    private List<String> queryWorkOrderTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "work_order", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!name.startsWith("flyway_")) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }
}
