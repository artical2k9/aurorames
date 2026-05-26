package com.mes.workorder;

import com.mes.common.security.privilege.PrivilegeRegistration;
import com.mes.common.security.privilege.PrivilegeRegistryClient;
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
public class WorkOrderServiceApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderServiceApplication.class);

    private final PrivilegeRegistryClient privilegeRegistryClient;

    public WorkOrderServiceApplication(PrivilegeRegistryClient privilegeRegistryClient) {
        this.privilegeRegistryClient = privilegeRegistryClient;
    }

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderServiceApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            privilegeRegistryClient.register("item-master", List.of(
                    new PrivilegeRegistration("item-master:records:view",   "View item master records"),
                    new PrivilegeRegistration("item-master:records:manage", "Create and update item master records"),
                    new PrivilegeRegistration("item-master:bom:manage",     "Author and release BOM revisions"),
                    new PrivilegeRegistration("item-master:eco:manage",     "Create and approve ECOs"),
                    new PrivilegeRegistration("item-master:udf:manage",     "Define and delete UDF fields")
            ));
            log.info("item-master privileges registered with IAM service");
        } catch (Exception e) {
            log.warn("Failed to register item-master privileges on startup (non-fatal): {}", e.getMessage());
        }
    }
}
