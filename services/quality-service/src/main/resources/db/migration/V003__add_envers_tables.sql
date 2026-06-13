-- Envers audit infrastructure. ValidityAuditStrategy requires revend (FK to revinfo.rev)
-- and revend_tstmp on every _aud table (created inline here from day one).

CREATE TABLE quality.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    actor    VARCHAR(255),
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE quality.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE quality.inspection_plan_aud (
    id           UUID         NOT NULL,
    rev          INT4         NOT NULL,
    revtype      INT2,
    revend       INTEGER      REFERENCES quality.revinfo (rev),
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    item_id      UUID,
    part_number  VARCHAR(100),
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_inspection_plan_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_inspection_plan_aud_rev FOREIGN KEY (rev) REFERENCES quality.revinfo (rev)
);

CREATE TABLE quality.inspection_plan_revision_aud (
    id                  UUID         NOT NULL,
    rev                 INT4         NOT NULL,
    revtype             INT2,
    revend              INTEGER      REFERENCES quality.revinfo (rev),
    revend_tstmp        TIMESTAMPTZ,
    inspection_plan_id  UUID,
    revision            INTEGER,
    revision_status     VARCHAR(20),
    name                VARCHAR(200),
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
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ,
    modified_by         VARCHAR(255),
    modified_at         TIMESTAMPTZ,
    CONSTRAINT pk_ipr_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_ipr_aud_rev FOREIGN KEY (rev) REFERENCES quality.revinfo (rev)
);

CREATE TABLE quality.inspection_characteristic_aud (
    id                    UUID          NOT NULL,
    rev                   INT4          NOT NULL,
    revtype               INT2,
    revend                INTEGER       REFERENCES quality.revinfo (rev),
    revend_tstmp          TIMESTAMPTZ,
    plan_revision_id      UUID,
    characteristic_number INTEGER,
    name                  VARCHAR(200),
    description           TEXT,
    source                VARCHAR(20),
    characteristic_type   VARCHAR(20),
    inspection_method     VARCHAR(255),
    gauge_type            VARCHAR(255),
    unit_of_measure       VARCHAR(20),
    sample_size_rule      VARCHAR(20),
    sample_size_count     INTEGER,
    recording_basis       VARCHAR(20),
    nominal_value         NUMERIC(18,6),
    lower_limit           NUMERIC(18,6),
    upper_limit           NUMERIC(18,6),
    expected_boolean      BOOLEAN,
    expression            VARCHAR(1000),
    custom_fields         JSONB,
    created_by            VARCHAR(255),
    created_at            TIMESTAMPTZ,
    modified_by           VARCHAR(255),
    modified_at           TIMESTAMPTZ,
    CONSTRAINT pk_ic_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_ic_aud_rev FOREIGN KEY (rev) REFERENCES quality.revinfo (rev)
);

CREATE TABLE quality.udf_field_definition_aud (
    id               UUID         NOT NULL,
    rev              INT4         NOT NULL,
    revtype          INT2,
    revend           INTEGER      REFERENCES quality.revinfo (rev),
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
    CONSTRAINT fk_udf_field_definition_aud_rev FOREIGN KEY (rev) REFERENCES quality.revinfo (rev)
);
