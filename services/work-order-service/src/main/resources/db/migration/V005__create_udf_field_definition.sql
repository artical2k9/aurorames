CREATE TABLE work_order.udf_field_definition (
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
