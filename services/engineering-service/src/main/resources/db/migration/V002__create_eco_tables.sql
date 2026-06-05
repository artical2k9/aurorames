CREATE TABLE engineering.engineering_change_order (
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

CREATE INDEX idx_eco_org_status ON engineering.engineering_change_order (org_id, status);

-- eco_affected_item: affected_item_id is a plain UUID reference to inventory.item_master.
-- No cross-schema FK — services are independently deployable (Q1 UUID-only confirmed).
CREATE TABLE engineering.eco_affected_item (
    eco_id          UUID NOT NULL,
    affected_item_id UUID NOT NULL,

    CONSTRAINT pk_eco_affected_item PRIMARY KEY (eco_id, affected_item_id),
    CONSTRAINT fk_eai_eco           FOREIGN KEY (eco_id) REFERENCES engineering.engineering_change_order (id)
);

-- eco_output_bom: bom_id is a plain UUID reference to inventory.bill_of_materials.
-- No cross-schema FK — BOM linkage is established via Kafka bom.released event.
CREATE TABLE engineering.eco_output_bom (
    eco_id UUID NOT NULL,
    bom_id UUID NOT NULL,

    CONSTRAINT pk_eco_output_bom  PRIMARY KEY (eco_id, bom_id),
    CONSTRAINT fk_eob_eco         FOREIGN KEY (eco_id) REFERENCES engineering.engineering_change_order (id)
);

CREATE INDEX idx_eco_output_bom_eco ON engineering.eco_output_bom (eco_id);
