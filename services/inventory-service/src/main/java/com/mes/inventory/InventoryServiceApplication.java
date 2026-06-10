package com.mes.inventory;

import com.mes.common.security.privilege.PrivilegeRegistration;
import com.mes.common.security.privilege.PrivilegeRegistryClient;
import com.mes.inventory.health.PrivilegeRegistrationHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.List;

@SpringBootApplication
@EnableJpaAuditing
public class InventoryServiceApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryServiceApplication.class);

    private final PrivilegeRegistryClient privilegeRegistryClient;
    private final PrivilegeRegistrationHealthIndicator privilegeHealthIndicator;

    public InventoryServiceApplication(PrivilegeRegistryClient privilegeRegistryClient,
                                       PrivilegeRegistrationHealthIndicator privilegeHealthIndicator) {
        this.privilegeRegistryClient = privilegeRegistryClient;
        this.privilegeHealthIndicator = privilegeHealthIndicator;
    }

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            privilegeRegistryClient.register("item-master", List.of(
                    new PrivilegeRegistration("item-master:records:view",     "View item master records"),
                    new PrivilegeRegistration("item-master:records:manage",   "Create and update item master records"),
                    new PrivilegeRegistration("item-master:revisions:approve",
                            "Approve or reject item revision submissions"),
                    new PrivilegeRegistration("item-master:bom:manage",       "Author BOM revisions"),
                    new PrivilegeRegistration("item-master:udf:manage",       "Define and delete UDF fields"),
                    new PrivilegeRegistration("bom:revisions:approve",
                            "Approve or reject BOM revision submissions")
            ));
            privilegeHealthIndicator.markRegistered();
            LOG.info("item-master privileges registered with IAM service");
        } catch (Exception e) {
            LOG.warn("Failed to register item-master privileges on startup (non-fatal): {}", e.getMessage());
        }
    }
}
