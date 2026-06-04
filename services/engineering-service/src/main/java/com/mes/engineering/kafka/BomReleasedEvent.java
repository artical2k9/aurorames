package com.mes.engineering.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// Field names must exactly match what BomEventPublisher (inventory-service) serialises.
// Canonical payload definition: data-model.md §Kafka Events.
@JsonIgnoreProperties(ignoreUnknown = true)
public class BomReleasedEvent {

    private String bomId;
    private String ecoId;
    private String orgId;
    private String parentItemId;
    private String bomRevision;

    public BomReleasedEvent() {}

    public String getBomId() {
        return bomId;
    }

    public void setBomId(String bomId) {
        this.bomId = bomId;
    }

    public String getEcoId() {
        return ecoId;
    }

    public void setEcoId(String ecoId) {
        this.ecoId = ecoId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(String parentItemId) {
        this.parentItemId = parentItemId;
    }

    public String getBomRevision() {
        return bomRevision;
    }

    public void setBomRevision(String bomRevision) {
        this.bomRevision = bomRevision;
    }

    public UUID getBomIdAsUuid() {
        return bomId != null ? UUID.fromString(bomId) : null;
    }

    public UUID getEcoIdAsUuid() {
        return ecoId != null ? UUID.fromString(ecoId) : null;
    }
}
