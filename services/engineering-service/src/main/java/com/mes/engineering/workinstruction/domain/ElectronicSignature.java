package com.mes.engineering.workinstruction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 21 CFR Part 11-style electronic signature recorded at approval time. Append-only:
 * there is intentionally no setter for the signed content and no update/delete repository
 * method. NOT Envers-audited — the row itself is the immutable audit record.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "wi_electronic_signature", schema = "engineering")
public class ElectronicSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wi_revision_id", nullable = false)
    private WorkInstructionRevision revision;

    @Column(name = "signer_user_id", nullable = false, length = 255)
    private String signerUserId;

    @Column(name = "signer_full_name", nullable = false, length = 255)
    private String signerFullName;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "meaning", nullable = false, length = 50)
    private String meaning;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public WorkInstructionRevision getRevision() {
        return revision;
    }

    public void setRevision(WorkInstructionRevision revision) {
        this.revision = revision;
    }

    public String getSignerUserId() {
        return signerUserId;
    }

    public void setSignerUserId(String signerUserId) {
        this.signerUserId = signerUserId;
    }

    public String getSignerFullName() {
        return signerFullName;
    }

    public void setSignerFullName(String signerFullName) {
        this.signerFullName = signerFullName;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
