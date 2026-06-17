package com.mes.routing.route.domain;

import com.mes.routing.common.OrgAuditedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Route header (ISA-95 Operations Definition) for a part identity, of a given route type.
 * Pins the BOM and inspection-plan revisions it was approved against (FR-004). At most one
 * Standard-type route per (org, part, revision) — enforced in the service (FR-004b).
 */
@Entity
@Audited
@Table(name = "route", schema = "routing")
public class Route extends OrgAuditedEntity {

    @Column(name = "part_id", nullable = false)
    private UUID partId;

    @Column(name = "part_revision", nullable = false, length = 20)
    private String partRevision;

    @Column(name = "route_type_id", nullable = false)
    private UUID routeTypeId;

    @Column(name = "bom_id")
    private UUID bomId;

    @Column(name = "bom_revision_id")
    private UUID bomRevisionId;

    @Column(name = "inspection_plan_revision_id")
    private UUID inspectionPlanRevisionId;

    @Column(name = "revision", nullable = false)
    private int revision = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RouteStatus status = RouteStatus.DRAFT;

    @Column(name = "reason_for_revision", nullable = false, length = 1000)
    private String reasonForRevision;

    @Column(name = "bom_revision_superseded", nullable = false)
    private boolean bomRevisionSuperseded = false;

    @Column(name = "inspection_plan_revision_superseded", nullable = false)
    private boolean inspectionPlanRevisionSuperseded = false;

    @Type(JsonBinaryType.class)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private Map<String, Object> customFields;

    /** Edit-lock holder (KC subject) — only this user may edit; null when unlocked (FR-031/032). */
    @Column(name = "lock_holder", length = 255)
    private String lockHolder;

    @Column(name = "locked_at")
    private Instant lockedAt;

    public UUID getPartId() {
        return partId;
    }

    public void setPartId(UUID partId) {
        this.partId = partId;
    }

    public String getPartRevision() {
        return partRevision;
    }

    public void setPartRevision(String partRevision) {
        this.partRevision = partRevision;
    }

    public UUID getRouteTypeId() {
        return routeTypeId;
    }

    public void setRouteTypeId(UUID routeTypeId) {
        this.routeTypeId = routeTypeId;
    }

    public UUID getBomId() {
        return bomId;
    }

    public void setBomId(UUID bomId) {
        this.bomId = bomId;
    }

    public UUID getBomRevisionId() {
        return bomRevisionId;
    }

    public void setBomRevisionId(UUID bomRevisionId) {
        this.bomRevisionId = bomRevisionId;
    }

    public UUID getInspectionPlanRevisionId() {
        return inspectionPlanRevisionId;
    }

    public void setInspectionPlanRevisionId(UUID inspectionPlanRevisionId) {
        this.inspectionPlanRevisionId = inspectionPlanRevisionId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
    }

    public String getReasonForRevision() {
        return reasonForRevision;
    }

    public void setReasonForRevision(String reasonForRevision) {
        this.reasonForRevision = reasonForRevision;
    }

    public boolean isBomRevisionSuperseded() {
        return bomRevisionSuperseded;
    }

    public void setBomRevisionSuperseded(boolean bomRevisionSuperseded) {
        this.bomRevisionSuperseded = bomRevisionSuperseded;
    }

    public boolean isInspectionPlanRevisionSuperseded() {
        return inspectionPlanRevisionSuperseded;
    }

    public void setInspectionPlanRevisionSuperseded(boolean inspectionPlanRevisionSuperseded) {
        this.inspectionPlanRevisionSuperseded = inspectionPlanRevisionSuperseded;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }

    public String getLockHolder() {
        return lockHolder;
    }

    public void setLockHolder(String lockHolder) {
        this.lockHolder = lockHolder;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }
}
