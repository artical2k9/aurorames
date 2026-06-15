package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.util.UUID;

/**
 * A route operation (logical manufacturing step). Type is derived from the sequence number
 * (Normal if unique, Parallel if shared — computed in the service, not stored); Optional/OSP
 * are explicit toggles. Significant-process and OSP-supplier are optional references (US3/US4).
 */
@Entity
@Audited
@Table(name = "route_operation", schema = "routing")
public class RouteOperation extends OrgAuditedEntity {

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "operation_number", nullable = false)
    private int operationNumber;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "optional", nullable = false)
    private boolean optional = false;

    @Column(name = "osp", nullable = false)
    private boolean osp = false;

    @Column(name = "significant_process_type_id")
    private UUID significantProcessTypeId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "clocking", nullable = false)
    private boolean clocking = true;

    @Column(name = "operation_revision", nullable = false)
    private int operationRevision = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false, length = 20)
    private OperationStatus operationStatus = OperationStatus.DRAFT;

    @Column(name = "governing_route_revision")
    private Integer governingRouteRevision;

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public int getOperationNumber() {
        return operationNumber;
    }

    public void setOperationNumber(int operationNumber) {
        this.operationNumber = operationNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean isOsp() {
        return osp;
    }

    public void setOsp(boolean osp) {
        this.osp = osp;
    }

    public UUID getSignificantProcessTypeId() {
        return significantProcessTypeId;
    }

    public void setSignificantProcessTypeId(UUID significantProcessTypeId) {
        this.significantProcessTypeId = significantProcessTypeId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public boolean isClocking() {
        return clocking;
    }

    public void setClocking(boolean clocking) {
        this.clocking = clocking;
    }

    public int getOperationRevision() {
        return operationRevision;
    }

    public void setOperationRevision(int operationRevision) {
        this.operationRevision = operationRevision;
    }

    public OperationStatus getOperationStatus() {
        return operationStatus;
    }

    public void setOperationStatus(OperationStatus operationStatus) {
        this.operationStatus = operationStatus;
    }

    public Integer getGoverningRouteRevision() {
        return governingRouteRevision;
    }

    public void setGoverningRouteRevision(Integer governingRouteRevision) {
        this.governingRouteRevision = governingRouteRevision;
    }
}
