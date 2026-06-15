-- MES-9 US3: operation detail — resources, labour standards, consumption, quality, tooling,
-- skills, work-instruction link, STEP-file reference. All children of route_operation.

CREATE TABLE routing.operation_resource (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id        UUID         NOT NULL,
    operation_id  UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    work_centre_id UUID        NOT NULL REFERENCES routing.work_centre (id),
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_operation_resource PRIMARY KEY (id)
);
CREATE INDEX idx_operation_resource_op ON routing.operation_resource (operation_id);

CREATE TABLE routing.labour_plan_line (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id              UUID         NOT NULL,
    operation_id        UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    labour_activity_type VARCHAR(20) NOT NULL,
    labour_plan_type_id UUID         REFERENCES routing.labour_plan_type (id),
    labour_code_id      UUID         REFERENCES routing.labour_code (id),
    time_value          NUMERIC(12,4) NOT NULL,
    basis               VARCHAR(10)  NOT NULL,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_labour_plan_line PRIMARY KEY (id)
);
CREATE INDEX idx_labour_plan_line_op ON routing.labour_plan_line (operation_id);

CREATE TABLE routing.material_consumption (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    bom_line_id  UUID         NOT NULL,
    mandatory    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_material_consumption PRIMARY KEY (id)
);
CREATE INDEX idx_material_consumption_op ON routing.material_consumption (operation_id);

CREATE TABLE routing.quality_variable_requirement (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    inspection_characteristic_id UUID NOT NULL,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_quality_variable_requirement PRIMARY KEY (id)
);
CREATE INDEX idx_quality_variable_op ON routing.quality_variable_requirement (operation_id);

CREATE TABLE routing.tooling_requirement (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    gage_or_tool_ref VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_tooling_requirement PRIMARY KEY (id)
);
CREATE INDEX idx_tooling_requirement_op ON routing.tooling_requirement (operation_id);

CREATE TABLE routing.skill_requirement (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    skill_id     UUID         NOT NULL,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_skill_requirement PRIMARY KEY (id)
);
CREATE INDEX idx_skill_requirement_op ON routing.skill_requirement (operation_id);

CREATE TABLE routing.work_instruction_link (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    work_instruction_id UUID  NOT NULL,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_work_instruction_link PRIMARY KEY (id)
);
CREATE INDEX idx_work_instruction_link_op ON routing.work_instruction_link (operation_id);

CREATE TABLE routing.step_file_reference (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    operation_id UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    reference    VARCHAR(500) NOT NULL,
    created_by    VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    modified_by   VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_step_file_reference PRIMARY KEY (id)
);
CREATE INDEX idx_step_file_reference_op ON routing.step_file_reference (operation_id);
