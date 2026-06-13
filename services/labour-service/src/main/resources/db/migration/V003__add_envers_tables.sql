-- MES-11: Envers audit tables (ValidityAuditStrategy — revend/revend_tstmp included from day one;
-- see ERR-MES-057: every @Audited entity needs a matching _aud table or schema validation fails).

CREATE TABLE labour.revinfo (
    rev      INT4 NOT NULL,
    revtstmp INT8,
    actor    VARCHAR(255),
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE SEQUENCE labour.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE labour.employee_aud (
    id                UUID NOT NULL,
    rev               INT4 NOT NULL,
    revtype           INT2,
    revend            INTEGER REFERENCES labour.revinfo (rev),
    revend_tstmp      TIMESTAMPTZ,
    org_id            UUID,
    employee_number   VARCHAR(40),
    first_name        VARCHAR(100),
    last_name         VARCHAR(100),
    email             VARCHAR(255),
    employment_status VARCHAR(20),
    hire_date         DATE,
    iam_user_id       VARCHAR(255),
    custom_fields     JSONB,
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ,
    modified_by       VARCHAR(255),
    modified_at       TIMESTAMPTZ,
    CONSTRAINT pk_employee_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_employee_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);

CREATE TABLE labour.skill_aud (
    id                     UUID NOT NULL,
    rev                    INT4 NOT NULL,
    revtype                INT2,
    revend                 INTEGER REFERENCES labour.revinfo (rev),
    revend_tstmp           TIMESTAMPTZ,
    org_id                 UUID,
    skill_code             VARCHAR(50),
    name                   VARCHAR(200),
    description            TEXT,
    category               VARCHAR(100),
    certification_required BOOLEAN,
    validity_months        INTEGER,
    active                 BOOLEAN,
    custom_fields          JSONB,
    created_by             VARCHAR(255),
    created_at             TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    CONSTRAINT pk_skill_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_skill_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);

CREATE TABLE labour.certification_aud (
    id                UUID NOT NULL,
    rev               INT4 NOT NULL,
    revtype           INT2,
    revend            INTEGER REFERENCES labour.revinfo (rev),
    revend_tstmp      TIMESTAMPTZ,
    org_id            UUID,
    employee_id       UUID,
    skill_id          UUID,
    award_date        DATE,
    expiry_date       DATE,
    assessor          VARCHAR(200),
    evidence_ref      VARCHAR(500),
    revoked           BOOLEAN,
    revoked_by        VARCHAR(255),
    revoked_at        TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    custom_fields     JSONB,
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ,
    modified_by       VARCHAR(255),
    modified_at       TIMESTAMPTZ,
    CONSTRAINT pk_certification_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_certification_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);

CREATE TABLE labour.training_event_aud (
    id               UUID NOT NULL,
    rev              INT4 NOT NULL,
    revtype          INT2,
    revend           INTEGER REFERENCES labour.revinfo (rev),
    revend_tstmp     TIMESTAMPTZ,
    org_id           UUID,
    title            VARCHAR(255),
    training_date    DATE,
    duration_minutes INTEGER,
    trainer          VARCHAR(200),
    notes            TEXT,
    custom_fields    JSONB,
    created_by       VARCHAR(255),
    created_at       TIMESTAMPTZ,
    modified_by      VARCHAR(255),
    modified_at      TIMESTAMPTZ,
    CONSTRAINT pk_training_event_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_training_event_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);

CREATE TABLE labour.training_attendance_aud (
    id                UUID NOT NULL,
    rev               INT4 NOT NULL,
    revtype           INT2,
    revend            INTEGER REFERENCES labour.revinfo (rev),
    revend_tstmp      TIMESTAMPTZ,
    training_event_id UUID,
    employee_id       UUID,
    outcome           VARCHAR(20),
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ,
    modified_by       VARCHAR(255),
    modified_at       TIMESTAMPTZ,
    CONSTRAINT pk_training_attendance_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_training_attendance_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);

-- UdfFieldDefinition from libs/mes-udf-lib is @Audited; must have _aud table in this
-- service's schema (ERR-MES-061).
CREATE TABLE labour.udf_field_definition_aud (
    id               UUID NOT NULL,
    rev              INT4 NOT NULL,
    revtype          INT2,
    revend           INTEGER REFERENCES labour.revinfo (rev),
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
    CONSTRAINT fk_udf_field_definition_aud_rev FOREIGN KEY (rev) REFERENCES labour.revinfo (rev)
);
