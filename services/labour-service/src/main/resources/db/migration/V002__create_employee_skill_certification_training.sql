-- MES-11: Labour Resources & Skills — core tables.
-- All audit columns are NOT NULL (Spring Data auditing fills them; AuditorAware bean present).

CREATE TABLE labour.employee (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id            UUID         NOT NULL,
    employee_number   VARCHAR(40)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255),
    employment_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    hire_date         DATE,
    iam_user_id       VARCHAR(255),
    custom_fields     JSONB,
    created_by        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by       VARCHAR(255) NOT NULL,
    modified_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_employee PRIMARY KEY (id),
    CONSTRAINT uq_employee_org_number UNIQUE (org_id, employee_number)
);

-- IAM link is unique per org where present (an IAM user maps to at most one employee).
CREATE UNIQUE INDEX uq_employee_org_iam_user
    ON labour.employee (org_id, iam_user_id)
    WHERE iam_user_id IS NOT NULL;

CREATE TABLE labour.skill (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                 UUID         NOT NULL,
    skill_code             VARCHAR(50)  NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    category               VARCHAR(100),
    certification_required BOOLEAN      NOT NULL DEFAULT true,
    validity_months        INTEGER,
    active                 BOOLEAN      NOT NULL DEFAULT true,
    custom_fields          JSONB,
    created_by             VARCHAR(255) NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by            VARCHAR(255) NOT NULL,
    modified_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_skill PRIMARY KEY (id),
    CONSTRAINT uq_skill_org_code UNIQUE (org_id, skill_code)
);

CREATE TABLE labour.certification (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id            UUID         NOT NULL,
    employee_id       UUID         NOT NULL,
    skill_id          UUID         NOT NULL,
    award_date        DATE         NOT NULL,
    expiry_date       DATE,
    assessor          VARCHAR(200),
    evidence_ref      VARCHAR(500),
    revoked           BOOLEAN      NOT NULL DEFAULT false,
    revoked_by        VARCHAR(255),
    revoked_at        TIMESTAMPTZ,
    revocation_reason VARCHAR(500),
    custom_fields     JSONB,
    created_by        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by       VARCHAR(255) NOT NULL,
    modified_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_certification PRIMARY KEY (id),
    CONSTRAINT fk_certification_employee FOREIGN KEY (employee_id) REFERENCES labour.employee (id),
    CONSTRAINT fk_certification_skill    FOREIGN KEY (skill_id)    REFERENCES labour.skill (id),
    CONSTRAINT uq_certification_emp_skill_award UNIQUE (employee_id, skill_id, award_date)
);

CREATE INDEX idx_certification_emp_skill_expiry
    ON labour.certification (employee_id, skill_id, expiry_date DESC);

CREATE INDEX idx_certification_org_expiry
    ON labour.certification (org_id, expiry_date);

CREATE TABLE labour.training_event (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id           UUID         NOT NULL,
    title            VARCHAR(255) NOT NULL,
    training_date    DATE         NOT NULL,
    duration_minutes INTEGER,
    trainer          VARCHAR(200),
    notes            TEXT,
    custom_fields    JSONB,
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by      VARCHAR(255) NOT NULL,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_training_event PRIMARY KEY (id)
);

CREATE TABLE labour.training_attendance (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    training_event_id UUID         NOT NULL,
    employee_id       UUID         NOT NULL,
    outcome           VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    created_by        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by       VARCHAR(255) NOT NULL,
    modified_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_training_attendance PRIMARY KEY (id),
    CONSTRAINT fk_attendance_event    FOREIGN KEY (training_event_id) REFERENCES labour.training_event (id),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id)       REFERENCES labour.employee (id),
    CONSTRAINT uq_attendance_event_employee UNIQUE (training_event_id, employee_id)
);

CREATE TABLE labour.training_event_skill (
    training_event_id UUID NOT NULL,
    skill_id          UUID NOT NULL,

    CONSTRAINT pk_training_event_skill PRIMARY KEY (training_event_id, skill_id),
    CONSTRAINT fk_tes_event FOREIGN KEY (training_event_id) REFERENCES labour.training_event (id),
    CONSTRAINT fk_tes_skill FOREIGN KEY (skill_id)          REFERENCES labour.skill (id)
);

-- UDF field definitions (mes-udf-lib @Entity lives in every consuming service's schema).
CREATE TABLE labour.udf_field_definition (
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
