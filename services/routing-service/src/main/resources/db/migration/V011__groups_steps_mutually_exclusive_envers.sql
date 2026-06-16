-- MES-9: Envers _aud tables for operation_group, operation_step, mutually_exclusive_set (ERR-MES-061).

CREATE TABLE routing.operation_group_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, route_id UUID, group_sequence_number INTEGER, name VARCHAR(255), optional BOOLEAN,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_operation_group_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_operation_group_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.operation_step_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, operation_id UUID, step_number INTEGER, step_sequence_number INTEGER,
    description VARCHAR(500), optional BOOLEAN,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_operation_step_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_operation_step_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.mutually_exclusive_set_aud (
    id UUID NOT NULL, rev INT4 NOT NULL, revtype INT2,
    revend INTEGER REFERENCES routing.revinfo (rev), revend_tstmp TIMESTAMPTZ,
    org_id UUID, route_id UUID, level VARCHAR(20), sequence_number INTEGER, member_ids JSONB,
    created_by VARCHAR(255), created_at TIMESTAMPTZ, modified_by VARCHAR(255), modified_at TIMESTAMPTZ,
    CONSTRAINT pk_mutually_exclusive_set_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_mutually_exclusive_set_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);
