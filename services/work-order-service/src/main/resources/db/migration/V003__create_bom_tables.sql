CREATE TABLE work_order.bill_of_materials (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id         UUID         NOT NULL,
    parent_item_id UUID         NOT NULL,
    bom_revision   VARCHAR(20)  NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    description    VARCHAR(500),
    eco_id         UUID,
    created_by     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by    VARCHAR(255) NOT NULL,
    modified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_bill_of_materials PRIMARY KEY (id),
    CONSTRAINT uq_bom_org_item_rev  UNIQUE (org_id, parent_item_id, bom_revision),
    CONSTRAINT fk_bom_parent_item   FOREIGN KEY (parent_item_id) REFERENCES work_order.item_master (id)
);

CREATE INDEX idx_bom_parent_item ON work_order.bill_of_materials (parent_item_id);
CREATE INDEX idx_bom_org_status  ON work_order.bill_of_materials (org_id, status);

CREATE TABLE work_order.bom_line (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    bom_id               UUID          NOT NULL,
    component_item_id    UUID          NOT NULL,
    quantity             NUMERIC(18,6) NOT NULL,
    unit_of_measure      VARCHAR(20)   NOT NULL,
    find_number          VARCHAR(20)   NOT NULL,
    reference_designators VARCHAR(500),
    effectivity_method   VARCHAR(10),
    effective_from_date  DATE,
    effective_to_date    DATE,
    effective_from_unit  VARCHAR(50),
    effective_to_unit    VARCHAR(50),
    created_by           VARCHAR(255)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modified_by          VARCHAR(255)  NOT NULL,
    modified_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_bom_line              PRIMARY KEY (id),
    CONSTRAINT fk_bom_line_bom          FOREIGN KEY (bom_id) REFERENCES work_order.bill_of_materials (id),
    CONSTRAINT fk_bom_line_component    FOREIGN KEY (component_item_id) REFERENCES work_order.item_master (id),
    CONSTRAINT chk_effectivity_method   CHECK (effectivity_method IS NULL OR effectivity_method IN ('DATE', 'UNIT'))
);

CREATE INDEX idx_bom_line_bom_id       ON work_order.bom_line (bom_id);
CREATE INDEX idx_bom_line_component_id ON work_order.bom_line (component_item_id);
CREATE INDEX idx_bom_line_bom_find     ON work_order.bom_line (bom_id, find_number);
