CREATE TABLE inventory.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    actor    VARCHAR(255),
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE inventory.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE inventory.item_master_aud (
    id                     UUID         NOT NULL,
    rev                    INT4         NOT NULL,
    revtype                INT2,
    org_id                 UUID,
    part_number            VARCHAR(100),
    revision               VARCHAR(20),
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
    status                 VARCHAR(20),
    created_by             VARCHAR(255),
    created_at             TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    CONSTRAINT pk_item_master_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_item_master_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);

CREATE TABLE inventory.bill_of_materials_aud (
    id             UUID         NOT NULL,
    rev            INT4         NOT NULL,
    revtype        INT2,
    org_id         UUID,
    parent_item_id UUID,
    bom_revision   VARCHAR(20),
    status         VARCHAR(10),
    description    VARCHAR(500),
    eco_id         UUID,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ,
    modified_by    VARCHAR(255),
    modified_at    TIMESTAMPTZ,
    CONSTRAINT pk_bom_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_bom_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);

CREATE TABLE inventory.bom_line_aud (
    id                    UUID          NOT NULL,
    rev                   INT4          NOT NULL,
    revtype               INT2,
    bom_id                UUID,
    component_item_id     UUID,
    quantity              NUMERIC(18,6),
    unit_of_measure       VARCHAR(20),
    find_number           VARCHAR(20),
    reference_designators VARCHAR(500),
    effectivity_method    VARCHAR(10),
    effective_from_date   DATE,
    effective_to_date     DATE,
    effective_from_unit   VARCHAR(50),
    effective_to_unit     VARCHAR(50),
    created_by            VARCHAR(255),
    created_at            TIMESTAMPTZ,
    modified_by           VARCHAR(255),
    modified_at           TIMESTAMPTZ,
    CONSTRAINT pk_bom_line_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_bom_line_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);
