-- MES-9: Envers audit tables (ValidityAuditStrategy). Every @Audited entity needs a
-- matching _aud table or Hibernate schema validation fails (ERR-MES-061).

CREATE TABLE routing.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    actor    VARCHAR(255),
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE routing.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE routing.work_centre_aud (
    id           UUID NOT NULL,
    rev          INT4 NOT NULL,
    revtype      INT2,
    revend       INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    code         VARCHAR(50),
    name         VARCHAR(200),
    description  TEXT,
    active       BOOLEAN,
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_work_centre_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_work_centre_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.labour_plan_type_aud (
    id           UUID NOT NULL,
    rev          INT4 NOT NULL,
    revtype      INT2,
    revend       INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    code         VARCHAR(50),
    name         VARCHAR(200),
    seeded       BOOLEAN,
    active       BOOLEAN,
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_labour_plan_type_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_labour_plan_type_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.labour_code_aud (
    id                  UUID NOT NULL,
    rev                 INT4 NOT NULL,
    revtype             INT2,
    revend              INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp        TIMESTAMPTZ,
    org_id              UUID,
    code                VARCHAR(50),
    name                VARCHAR(200),
    labour_plan_type_id UUID,
    active              BOOLEAN,
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ,
    modified_by         VARCHAR(255),
    modified_at         TIMESTAMPTZ,
    CONSTRAINT pk_labour_code_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_labour_code_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.route_type_aud (
    id           UUID NOT NULL,
    rev          INT4 NOT NULL,
    revtype      INT2,
    revend       INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    code         VARCHAR(50),
    name         VARCHAR(200),
    is_standard  BOOLEAN,
    seeded       BOOLEAN,
    active       BOOLEAN,
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_route_type_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_route_type_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.significant_process_type_aud (
    id                     UUID NOT NULL,
    rev                    INT4 NOT NULL,
    revtype                INT2,
    revend                 INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp           TIMESTAMPTZ,
    org_id                 UUID,
    code                   VARCHAR(50),
    name                   VARCHAR(200),
    required_approver_role VARCHAR(100),
    active                 BOOLEAN,
    created_by             VARCHAR(255),
    created_at             TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    CONSTRAINT pk_significant_process_type_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_significant_process_type_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

CREATE TABLE routing.supplier_aud (
    id           UUID NOT NULL,
    rev          INT4 NOT NULL,
    revtype      INT2,
    revend       INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    code         VARCHAR(50),
    name         VARCHAR(200),
    active       BOOLEAN,
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_supplier_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_supplier_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);

-- UdfFieldDefinition from libs/mes-udf-lib is @Audited; needs an _aud table here (ERR-MES-061).
CREATE TABLE routing.udf_field_definition_aud (
    id               UUID NOT NULL,
    rev              INT4 NOT NULL,
    revtype          INT2,
    revend           INTEGER REFERENCES routing.revinfo (rev),
    revend_tstmp     TIMESTAMPTZ,
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
    CONSTRAINT fk_udf_field_definition_aud_rev FOREIGN KEY (rev) REFERENCES routing.revinfo (rev)
);
