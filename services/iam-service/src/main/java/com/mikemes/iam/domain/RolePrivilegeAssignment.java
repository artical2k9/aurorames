package com.mikemes.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_privilege", schema = "iam")
@Audited
public class RolePrivilegeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privilege_id", nullable = false)
    private Privilege privilege;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @CreatedDate
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @CreatedBy
    @Column(name = "granted_by", nullable = false, updatable = false, length = 200)
    private String grantedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 200)
    private String revokedBy;

    protected RolePrivilegeAssignment() {}

    public RolePrivilegeAssignment(Role role, Privilege privilege, UUID orgId) {
        this.role = role;
        this.privilege = privilege;
        this.orgId = orgId;
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Privilege getPrivilege() {
        return privilege;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(String revokedBy) {
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
    }
}
