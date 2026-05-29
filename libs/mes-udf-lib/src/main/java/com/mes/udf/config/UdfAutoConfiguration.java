package com.mes.udf.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration entry point for mes-udf-lib.
 *
 * <p>Consuming services MUST set the following property so Hibernate resolves the
 * {@code udf_field_definition} table in the correct schema:
 * <pre>
 *   spring.jpa.properties.hibernate.default_schema: &lt;schema-name&gt;
 * </pre>
 * For work-order-service this is {@code work_order}. Other services must supply their
 * own schema name. Without this property Hibernate falls back to the JDBC connection's
 * default schema, which may or may not match.
 */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "com.mes.udf")
@ComponentScan(basePackages = "com.mes.udf")
public class UdfAutoConfiguration {
}
