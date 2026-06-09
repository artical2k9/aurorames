-- MES-114: Envers audit tables for item and item_revision.
-- Preserves pre-migration audit history by renaming rather than dropping the legacy table.

ALTER TABLE inventory.item_master_aud RENAME TO item_master_aud_legacy;

CREATE TABLE inventory.item_aud (
    id          UUID         NOT NULL,
    rev         INTEGER      NOT NULL,
    revtype     SMALLINT,
    org_id      UUID,
    part_number VARCHAR(100),
    created_by  VARCHAR(255),
    created_at  TIMESTAMPTZ,
    CONSTRAINT pk_item_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_item_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);

CREATE TABLE inventory.item_revision_aud (
    id                     UUID         NOT NULL,
    rev                    INTEGER      NOT NULL,
    revtype                SMALLINT,
    item_id                UUID,
    revision               INTEGER,
    revision_status        VARCHAR(20),
    description            VARCHAR(500),
    unit_of_measure        VARCHAR(20),
    cage_code              VARCHAR(10),
    classification         VARCHAR(30),
    make_buy_code          VARCHAR(10),
    traceability_method    VARCHAR(15),
    shelf_life_controlled  BOOLEAN,
    shelf_life_days        INTEGER,
    step_part_ref          VARCHAR(255),
    counterfeit_risk_level VARCHAR(10),
    approved_suppliers     JSONB,
    verification_required  BOOLEAN,
    custom_fields          JSONB,
    submitted_by           VARCHAR(255),
    submitted_at           TIMESTAMPTZ,
    approved_by            VARCHAR(255),
    approved_at            TIMESTAMPTZ,
    rejected_by            VARCHAR(255),
    rejected_at            TIMESTAMPTZ,
    rejection_reason       VARCHAR(500),
    created_by             VARCHAR(255),
    created_at             TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    CONSTRAINT pk_item_revision_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_item_revision_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);
