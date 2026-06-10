-- MES-114: Introduce bom identity + bom_revision versioned tables.
-- bom holds identity (org_id, parent_item_id); bom_revision holds all data fields.

CREATE TABLE inventory.bom (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id          UUID         NOT NULL,
    parent_item_id  UUID         NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_bom              PRIMARY KEY (id),
    CONSTRAINT uq_bom_org_item     UNIQUE (org_id, parent_item_id),
    CONSTRAINT fk_bom_parent_item  FOREIGN KEY (parent_item_id) REFERENCES inventory.item (id)
);

CREATE INDEX idx_bom_org             ON inventory.bom (org_id);
CREATE INDEX idx_bom_identity_item    ON inventory.bom (parent_item_id);

CREATE TABLE inventory.bom_revision (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    bom_id               UUID          NOT NULL,
    revision             INTEGER       NOT NULL,
    revision_status      VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',

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

    created_by           VARCHAR(255)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modified_by          VARCHAR(255)  NOT NULL,
    modified_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_bom_revision             PRIMARY KEY (id),
    CONSTRAINT fk_bom_revision_bom         FOREIGN KEY (bom_id) REFERENCES inventory.bom (id),
    CONSTRAINT uq_bom_revision_bom_rev     UNIQUE (bom_id, revision),
    CONSTRAINT chk_bom_revision_status     CHECK (revision_status IN ('DRAFT','PENDING_APPROVAL','APPROVED'))
);

-- Enforces at most one DRAFT per bom identity at the DB level.
CREATE UNIQUE INDEX uq_bom_revision_one_draft
    ON inventory.bom_revision (bom_id)
    WHERE revision_status = 'DRAFT';

CREATE INDEX idx_bom_revision_bom_id ON inventory.bom_revision (bom_id);
CREATE INDEX idx_bom_revision_status ON inventory.bom_revision (bom_id, revision_status);
