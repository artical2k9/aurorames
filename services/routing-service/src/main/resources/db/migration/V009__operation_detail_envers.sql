-- MES-9: Envers _aud tables for the operation-detail entities (ERR-MES-061).

CREATE TABLE routing.operation_resource_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, work_centre_id UUID,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_operation_resource_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_operation_resource_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.labour_plan_line_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, labour_activity_type VARCHAR(20), labour_plan_type_id UUID,
    labour_code_id UUID, time_value NUMERIC(12,4), basis VARCHAR(10),
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_labour_plan_line_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_labour_plan_line_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.material_consumption_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, bom_line_id UUID, mandatory BOOLEAN,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_material_consumption_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_material_consumption_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.quality_variable_requirement_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, inspection_characteristic_id UUID,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_quality_variable_requirement_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_quality_variable_requirement_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.tooling_requirement_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, gage_or_tool_ref VARCHAR(100), description VARCHAR(500),
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_tooling_requirement_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_tooling_requirement_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.skill_requirement_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, skill_id UUID,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_skill_requirement_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_skill_requirement_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.work_instruction_link_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, work_instruction_id UUID,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_work_instruction_link_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_work_instruction_link_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.step_file_reference_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, reference VARCHAR(500),
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_step_file_reference_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_step_file_reference_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);
