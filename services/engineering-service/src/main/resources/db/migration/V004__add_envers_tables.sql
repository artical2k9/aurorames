CREATE TABLE engineering.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    actor    VARCHAR(255),
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE engineering.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE engineering.engineering_change_order_aud (
    id              UUID         NOT NULL,
    rev             INT4         NOT NULL,
    revtype         INT2,
    org_id          UUID,
    eco_number      VARCHAR(30),
    title           VARCHAR(255),
    description     TEXT,
    status          VARCHAR(15),
    initiated_by    VARCHAR(255),
    approved_by     VARCHAR(255),
    approved_at     TIMESTAMPTZ,
    implemented_at  TIMESTAMPTZ,
    created_by      VARCHAR(255),
    created_at      TIMESTAMPTZ,
    modified_by     VARCHAR(255),
    modified_at     TIMESTAMPTZ,
    CONSTRAINT pk_eco_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_eco_aud_rev FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev)
);

-- UdfFieldDefinition from libs/mes-udf-lib is @Audited; must have _aud table in this
-- service's schema. See ERR-MES-061: omitting lib @Audited entities causes schema-validation
-- failure at startup.
CREATE TABLE engineering.udf_field_definition_aud (
    id               UUID         NOT NULL,
    rev              INT4         NOT NULL,
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
    CONSTRAINT pk_udf_field_definition_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_udf_field_definition_aud_rev FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev)
);
