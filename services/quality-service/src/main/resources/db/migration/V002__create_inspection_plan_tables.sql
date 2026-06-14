-- Inspection plan (control plan) tables: root + revision + characteristic.
-- Revision lifecycle mirrors the BOM pattern (DRAFT -> PENDING_APPROVAL -> APPROVED).

CREATE TABLE quality.inspection_plan (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    item_id      UUID         NOT NULL,
    part_number  VARCHAR(100) NOT NULL,
    created_by   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by  VARCHAR(255) NOT NULL,
    modified_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_inspection_plan        PRIMARY KEY (id),
    CONSTRAINT uq_inspection_plan_item   UNIQUE (org_id, item_id)
);

CREATE TABLE quality.inspection_plan_revision (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    inspection_plan_id  UUID         NOT NULL,
    revision            INTEGER      NOT NULL,
    revision_status     VARCHAR(20)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    reason_for_revision VARCHAR(500),
    custom_fields       JSONB,
    submitted_by        VARCHAR(255),
    submitted_at        TIMESTAMPTZ,
    approved_by         VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    rejected_by         VARCHAR(255),
    rejected_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    created_by          VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by         VARCHAR(255) NOT NULL,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_inspection_plan_revision      PRIMARY KEY (id),
    CONSTRAINT fk_ipr_plan                      FOREIGN KEY (inspection_plan_id)
                                                REFERENCES quality.inspection_plan (id),
    CONSTRAINT uq_ipr_plan_revision             UNIQUE (inspection_plan_id, revision)
);

-- One open draft per plan.
CREATE UNIQUE INDEX uq_ipr_one_draft
    ON quality.inspection_plan_revision (inspection_plan_id)
    WHERE revision_status = 'DRAFT';

CREATE TABLE quality.inspection_characteristic (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    plan_revision_id      UUID          NOT NULL,
    characteristic_number INTEGER       NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    description           TEXT,
    source                VARCHAR(20)   NOT NULL,
    characteristic_type   VARCHAR(20)   NOT NULL,
    inspection_method     VARCHAR(255),
    gauge_type            VARCHAR(255),
    unit_of_measure       VARCHAR(20),
    sample_size_rule      VARCHAR(20)   NOT NULL,
    sample_size_count     INTEGER,
    recording_basis       VARCHAR(20)   NOT NULL,
    nominal_value         NUMERIC(18,6),
    lower_limit           NUMERIC(18,6),
    upper_limit           NUMERIC(18,6),
    expected_boolean      BOOLEAN,
    expression            VARCHAR(1000),
    custom_fields         JSONB,
    created_by            VARCHAR(255)  NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    modified_by           VARCHAR(255)  NOT NULL,
    modified_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_inspection_characteristic   PRIMARY KEY (id),
    CONSTRAINT fk_ic_revision                 FOREIGN KEY (plan_revision_id)
                                              REFERENCES quality.inspection_plan_revision (id),
    CONSTRAINT uq_ic_revision_number          UNIQUE (plan_revision_id, characteristic_number)
);

CREATE INDEX idx_ipr_plan          ON quality.inspection_plan_revision (inspection_plan_id);
CREATE INDEX idx_ic_revision       ON quality.inspection_characteristic (plan_revision_id);

-- UDF definitions (mes-udf-lib UdfFieldDefinition entity).
CREATE TABLE quality.udf_field_definition (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id           UUID         NOT NULL,
    module_key       VARCHAR(50)  NOT NULL,
    field_key        VARCHAR(100) NOT NULL,
    label            VARCHAR(255) NOT NULL,
    field_type       VARCHAR(15)  NOT NULL,
    required         BOOLEAN      NOT NULL DEFAULT false,
    default_value    VARCHAR(500),
    list_options     JSONB,
    validation_rules JSONB,
    display_order    INTEGER      NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by      VARCHAR(255) NOT NULL,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_udf_field_definition     PRIMARY KEY (id),
    CONSTRAINT uq_udf_org_module_field_key UNIQUE (org_id, module_key, field_key)
);
