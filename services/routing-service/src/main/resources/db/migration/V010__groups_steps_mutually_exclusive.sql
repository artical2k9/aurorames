-- MES-9 US4/US5/US6: operation groups, operation steps, and mutually-exclusive sets.

CREATE TABLE routing.operation_group (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                UUID         NOT NULL,
    route_id              UUID         NOT NULL REFERENCES routing.route (id) ON DELETE CASCADE,
    group_sequence_number INTEGER      NOT NULL,
    name                  VARCHAR(255),
    optional              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by            VARCHAR(255) NOT NULL, created_at  TIMESTAMPTZ NOT NULL,
    modified_by           VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_operation_group PRIMARY KEY (id)
);
CREATE INDEX idx_operation_group_route ON routing.operation_group (route_id);

CREATE TABLE routing.operation_step (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id               UUID         NOT NULL,
    operation_id         UUID         NOT NULL REFERENCES routing.route_operation (id) ON DELETE CASCADE,
    step_number          INTEGER      NOT NULL,
    step_sequence_number INTEGER      NOT NULL,
    description          VARCHAR(500),
    optional             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by           VARCHAR(255) NOT NULL, created_at  TIMESTAMPTZ NOT NULL,
    modified_by          VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_operation_step PRIMARY KEY (id),
    CONSTRAINT uq_operation_step_number UNIQUE (operation_id, step_number)
);
CREATE INDEX idx_operation_step_op ON routing.operation_step (operation_id);

CREATE TABLE routing.mutually_exclusive_set (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id          UUID         NOT NULL,
    route_id        UUID         NOT NULL REFERENCES routing.route (id) ON DELETE CASCADE,
    level           VARCHAR(20)  NOT NULL,
    sequence_number INTEGER      NOT NULL,
    member_ids      JSONB        NOT NULL,
    created_by      VARCHAR(255) NOT NULL, created_at  TIMESTAMPTZ NOT NULL,
    modified_by     VARCHAR(255) NOT NULL, modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_mutually_exclusive_set PRIMARY KEY (id)
);
CREATE INDEX idx_mutually_exclusive_set_route ON routing.mutually_exclusive_set (route_id);
