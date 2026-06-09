-- MES-114: Introduce item identity + item_revision versioned tables.
-- item holds identity (org_id, part_number); item_revision holds all data fields.

CREATE TABLE inventory.item (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    part_number VARCHAR(100) NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_item          PRIMARY KEY (id),
    CONSTRAINT uq_item_org_part UNIQUE (org_id, part_number)
);

CREATE INDEX idx_item_org ON inventory.item (org_id);

CREATE TABLE inventory.item_revision (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    item_id                UUID         NOT NULL,
    revision               INTEGER      NOT NULL,
    revision_status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    description            VARCHAR(500) NOT NULL,
    unit_of_measure        VARCHAR(20)  NOT NULL,
    cage_code              VARCHAR(10),
    classification         VARCHAR(30)  NOT NULL,
    make_buy_code          VARCHAR(10)  NOT NULL,
    traceability_method    VARCHAR(15)  NOT NULL,
    shelf_life_controlled  BOOLEAN      NOT NULL DEFAULT false,
    shelf_life_days        INTEGER,
    step_part_ref          VARCHAR(255),
    counterfeit_risk_level VARCHAR(10),
    approved_suppliers     JSONB,
    verification_required  BOOLEAN      NOT NULL DEFAULT false,
    custom_fields          JSONB,

    submitted_by     VARCHAR(255),
    submitted_at     TIMESTAMPTZ,
    approved_by      VARCHAR(255),
    approved_at      TIMESTAMPTZ,
    rejected_by      VARCHAR(255),
    rejected_at      TIMESTAMPTZ,
    rejection_reason VARCHAR(500),

    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_revision          PRIMARY KEY (id),
    CONSTRAINT fk_item_revision_item     FOREIGN KEY (item_id) REFERENCES inventory.item (id),
    CONSTRAINT uq_item_revision_item_rev UNIQUE (item_id, revision),
    CONSTRAINT chk_item_shelf_life       CHECK (shelf_life_controlled = false OR shelf_life_days IS NOT NULL),
    CONSTRAINT chk_item_revision_status  CHECK (revision_status IN ('DRAFT','PENDING_APPROVAL','APPROVED'))
);

-- Enforces at most one DRAFT per item identity at the DB level.
CREATE UNIQUE INDEX uq_item_revision_one_draft
    ON inventory.item_revision (item_id)
    WHERE revision_status = 'DRAFT';

CREATE INDEX idx_item_revision_item_id ON inventory.item_revision (item_id);
CREATE INDEX idx_item_revision_status  ON inventory.item_revision (item_id, revision_status);
