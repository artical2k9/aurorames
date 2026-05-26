CREATE TABLE work_order.item_master (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                UUID         NOT NULL,
    part_number           VARCHAR(100) NOT NULL,
    revision              VARCHAR(20)  NOT NULL,
    description           VARCHAR(500) NOT NULL,
    unit_of_measure       VARCHAR(20)  NOT NULL,
    cage_code             VARCHAR(10),
    classification        VARCHAR(30)  NOT NULL,
    make_buy_code         VARCHAR(10)  NOT NULL,
    traceability_method   VARCHAR(15)  NOT NULL,
    shelf_life_controlled BOOLEAN      NOT NULL DEFAULT false,
    shelf_life_days       INTEGER,
    step_part_ref         VARCHAR(255),
    counterfeit_risk_level VARCHAR(10),
    approved_suppliers    JSONB,
    verification_required BOOLEAN      NOT NULL DEFAULT false,
    custom_fields         JSONB,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by            VARCHAR(255) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by           VARCHAR(255) NOT NULL,
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_master PRIMARY KEY (id),
    CONSTRAINT uq_item_master_org_part_rev UNIQUE (org_id, part_number, revision),
    CONSTRAINT chk_shelf_life CHECK (shelf_life_controlled = false OR shelf_life_days IS NOT NULL)
);

CREATE INDEX idx_item_master_org_part_rev ON work_order.item_master (org_id, part_number, revision);
CREATE INDEX idx_item_master_org_status   ON work_order.item_master (org_id, status);
CREATE INDEX idx_item_master_org_class    ON work_order.item_master (org_id, classification);
