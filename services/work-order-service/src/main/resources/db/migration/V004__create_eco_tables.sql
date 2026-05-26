CREATE TABLE work_order.engineering_change_order (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id           UUID         NOT NULL,
    eco_number       VARCHAR(30)  UNIQUE,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(15)  NOT NULL DEFAULT 'DRAFT',
    initiated_by     VARCHAR(255) NOT NULL,
    approved_by      VARCHAR(255),
    approved_at      TIMESTAMPTZ,
    implemented_at   TIMESTAMPTZ,
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by      VARCHAR(255) NOT NULL,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_eco PRIMARY KEY (id)
);

CREATE INDEX idx_eco_org_status ON work_order.engineering_change_order (org_id, status);

ALTER TABLE work_order.bill_of_materials
    ADD CONSTRAINT fk_bom_eco
    FOREIGN KEY (eco_id) REFERENCES work_order.engineering_change_order (id);

CREATE TABLE work_order.eco_affected_item (
    eco_id  UUID NOT NULL,
    item_id UUID NOT NULL,

    CONSTRAINT pk_eco_affected_item  PRIMARY KEY (eco_id, item_id),
    CONSTRAINT fk_eai_eco            FOREIGN KEY (eco_id)  REFERENCES work_order.engineering_change_order (id),
    CONSTRAINT fk_eai_item           FOREIGN KEY (item_id) REFERENCES work_order.item_master (id)
);
