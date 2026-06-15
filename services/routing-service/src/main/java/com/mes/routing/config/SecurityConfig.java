package com.mes.routing.config;

import com.mes.common.security.annotation.EnableMESSecurity;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the shared MES resource-server security (Keycloak OIDC + RBAC via
 * {@code @RequiresPrivilege}). routing-service exposes no internal/webhook endpoints,
 * so only the standard resource-server chain from {@link EnableMESSecurity} is needed.
 */
@Configuration
@EnableMESSecurity
public class SecurityConfig {
}
