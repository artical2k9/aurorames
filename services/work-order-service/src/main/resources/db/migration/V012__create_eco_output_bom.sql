CREATE TABLE work_order.eco_output_bom (
    eco_id UUID NOT NULL,
    bom_id UUID NOT NULL,

    CONSTRAINT pk_eco_output_bom  PRIMARY KEY (eco_id, bom_id),
    CONSTRAINT fk_eob_eco         FOREIGN KEY (eco_id) REFERENCES work_order.engineering_change_order (id),
    CONSTRAINT fk_eob_bom         FOREIGN KEY (bom_id) REFERENCES work_order.bill_of_materials (id)
);

CREATE INDEX idx_eco_output_bom_eco ON work_order.eco_output_bom (eco_id);
