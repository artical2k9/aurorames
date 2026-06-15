-- MES-9 US1/US2: route header + route operations.

CREATE TABLE routing.route (
    id                                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                              UUID         NOT NULL,
    part_id                             UUID         NOT NULL,
    part_revision                       VARCHAR(20)  NOT NULL,
    route_type_id                       UUID         NOT NULL REFERENCES routing.route_type (id),
    bom_id                              UUID,
    bom_revision_id                     UUID,
    inspection_plan_revision_id         UUID,
    revision                            INTEGER      NOT NULL DEFAULT 1,
    status                              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    reason_for_revision                 VARCHAR(1000) NOT NULL,
    bom_revision_superseded             BOOLEAN      NOT NULL DEFAULT FALSE,
    inspection_plan_revision_superseded BOOLEAN      NOT NULL DEFAULT FALSE,
    custom_fields                       JSONB,
    created_by                          VARCHAR(255) NOT NULL,
    created_at                          TIMESTAMPTZ  NOT NULL,
    modified_by                         VARCHAR(255) NOT NULL,
    modified_at                         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_route PRIMARY KEY (id)
);

CREATE INDEX idx_route_org_part ON routing.route (org_id, part_id, part_revision);

CREATE TABLE routing.route_operation (
    id                          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                      UUID         NOT NULL,
    route_id                    UUID         NOT NULL REFERENCES routing.route (id),
    operation_number            INTEGER      NOT NULL,
    sequence_number             INTEGER      NOT NULL,
    description                 VARCHAR(500),
    optional                    BOOLEAN      NOT NULL DEFAULT FALSE,
    osp                         BOOLEAN      NOT NULL DEFAULT FALSE,
    significant_process_type_id UUID         REFERENCES routing.significant_process_type (id),
    supplier_id                 UUID         REFERENCES routing.supplier (id),
    group_id                    UUID,
    clocking                    BOOLEAN      NOT NULL DEFAULT TRUE,
    operation_revision          INTEGER      NOT NULL DEFAULT 1,
    operation_status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    governing_route_revision    INTEGER,
    created_by                  VARCHAR(255) NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL,
    modified_by                 VARCHAR(255) NOT NULL,
    modified_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_route_operation PRIMARY KEY (id),
    CONSTRAINT uq_route_operation_number UNIQUE (route_id, operation_number)
);

CREATE INDEX idx_route_operation_route ON routing.route_operation (route_id);
