package com.mikemes.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "privilege", schema = "iam")
public class Privilege {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "privilege_key", nullable = false, unique = true, length = 200)
    private String privilegeKey;

    @Column(name = "module_name", nullable = false, length = 100)
    private String moduleName;

    @Column(length = 500)
    private String description;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "registered_by_service", nullable = false, length = 100)
    private String registeredByService;

    protected Privilege() {}

    public Privilege(String privilegeKey, String moduleName, String registeredByService) {
        this.privilegeKey = privilegeKey;
        this.moduleName = moduleName;
        this.registeredByService = registeredByService;
        this.registeredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPrivilegeKey() {
        return privilegeKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public String getRegisteredByService() {
        return registeredByService;
    }
}
