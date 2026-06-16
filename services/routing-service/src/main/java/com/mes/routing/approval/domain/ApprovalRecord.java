package com.mes.routing.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable e-signature approval entry (§V, FR-024). Append-only: no setter mutates an existing
 * row and the repository exposes no update/delete — the row itself is the audit record, so it is
 * intentionally NOT Envers-audited. A route revision approval is satisfied by one standard approval
 * plus, for each distinct significant-process type on the route, one approval from a holder of that
 * type's required approver role. Operation-revision approvals are recorded at the operation level.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "approval_record", schema = "routing")
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 30)
    private ApprovalSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(name = "meaning", nullable = false, length = 100)
    private String meaning;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "significant_process_approver", nullable = false)
    private boolean significantProcessApprover = false;

    @Column(name = "significant_process_type_id")
    private UUID significantProcessTypeId;

    /** For an operation-revision approval: the route revision governing it at approval time (US8 audit). */
    @Column(name = "governing_route_revision")
    private Integer governingRouteRevision;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public ApprovalSubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(ApprovalSubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public boolean isSignificantProcessApprover() {
        return significantProcessApprover;
    }

    public void setSignificantProcessApprover(boolean significantProcessApprover) {
        this.significantProcessApprover = significantProcessApprover;
    }

    public UUID getSignificantProcessTypeId() {
        return significantProcessTypeId;
    }

    public void setSignificantProcessTypeId(UUID significantProcessTypeId) {
        this.significantProcessTypeId = significantProcessTypeId;
    }

    public Integer getGoverningRouteRevision() {
        return governingRouteRevision;
    }

    public void setGoverningRouteRevision(Integer governingRouteRevision) {
        this.governingRouteRevision = governingRouteRevision;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
