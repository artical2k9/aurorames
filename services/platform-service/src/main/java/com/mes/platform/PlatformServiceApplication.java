package com.mes.platform;

import com.mes.common.security.privilege.PrivilegeRegistration;
import com.mes.common.security.privilege.PrivilegeRegistryClient;
import com.mes.platform.health.PrivilegeRegistrationHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.List;

@SpringBootApplication
public class PlatformServiceApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformServiceApplication.class);

    private final PrivilegeRegistryClient privilegeRegistryClient;
    private final PrivilegeRegistrationHealthIndicator privilegeHealthIndicator;

    public PlatformServiceApplication(PrivilegeRegistryClient privilegeRegistryClient,
                                      PrivilegeRegistrationHealthIndicator privilegeHealthIndicator) {
        this.privilegeRegistryClient = privilegeRegistryClient;
        this.privilegeHealthIndicator = privilegeHealthIndicator;
    }

    public static void main(String[] args) {
        SpringApplication.run(PlatformServiceApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            privilegeRegistryClient.register("platform", List.of(
                    new PrivilegeRegistration("platform:config:read",   "View platform configuration"),
                    new PrivilegeRegistration("platform:config:manage", "Create and update platform configuration"),
                    new PrivilegeRegistration("settings:uom:read",      "View units of measure"),
                    new PrivilegeRegistration("settings:uom:write",     "Create and update units of measure")
            ));
            privilegeHealthIndicator.markRegistered();
            LOG.info("platform privileges registered with IAM service");
        } catch (Exception e) {
            LOG.warn("Failed to register platform privileges on startup (non-fatal): {}", e.getMessage());
        }
    }
}
