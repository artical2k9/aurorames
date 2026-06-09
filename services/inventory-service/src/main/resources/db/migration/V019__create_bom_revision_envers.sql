-- MES-114: Envers audit tables for bom and bom_revision.
-- Preserves pre-migration audit history by renaming rather than dropping the legacy table.

ALTER TABLE inventory.bill_of_materials_aud RENAME TO bill_of_materials_aud_legacy;

CREATE TABLE inventory.bom_aud (
    id             UUID         NOT NULL,
    rev            INT4         NOT NULL,
    revtype        INT2,
    org_id         UUID,
    parent_item_id UUID,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ,
    CONSTRAINT pk_bom_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_bom_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);

CREATE TABLE inventory.bom_revision_aud (
    id                   UUID          NOT NULL,
    rev                  INT4          NOT NULL,
    revtype              INT2,
    bom_id               UUID,
    revision             INTEGER,
    revision_status      VARCHAR(20),
    description          VARCHAR(500),
    eco_id               UUID,
    reason_for_revision  VARCHAR(500),
    production_line      VARCHAR(200),
    bom_type             VARCHAR(30),
    effectivity_type     VARCHAR(10),
    custom_fields        JSONB,
    submitted_by         VARCHAR(255),
    submitted_at         TIMESTAMPTZ,
    approved_by          VARCHAR(255),
    approved_at          TIMESTAMPTZ,
    rejected_by          VARCHAR(255),
    rejected_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(500),
    created_by           VARCHAR(255),
    created_at           TIMESTAMPTZ,
    modified_by          VARCHAR(255),
    modified_at          TIMESTAMPTZ,
    CONSTRAINT pk_bom_revision_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_bom_revision_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);
