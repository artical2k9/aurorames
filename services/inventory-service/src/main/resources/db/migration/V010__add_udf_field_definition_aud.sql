-- UdfFieldDefinition (mes-udf-lib) is @Audited; Hibernate Envers ValidityAuditStrategy
-- requires this table and the revend/revend_tstmp columns to exist at startup (ERR-MES-057).
CREATE TABLE inventory.udf_field_definition_aud (
    id               UUID          NOT NULL,
    rev              INT4          NOT NULL,
    revtype          INT2,
    org_id           UUID,
    module_key       VARCHAR(50),
    field_key        VARCHAR(100),
    label            VARCHAR(255),
    field_type       VARCHAR(15),
    required         BOOLEAN,
    default_value    VARCHAR(500),
    list_options     JSONB,
    validation_rules JSONB,
    display_order    INTEGER,
    active           BOOLEAN,
    created_by       VARCHAR(255),
    created_at       TIMESTAMPTZ,
    modified_by      VARCHAR(255),
    modified_at      TIMESTAMPTZ,
    revend           INT4          REFERENCES inventory.revinfo (rev),
    revend_tstmp     TIMESTAMPTZ,
    CONSTRAINT pk_udf_field_definition_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_udf_field_definition_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);
