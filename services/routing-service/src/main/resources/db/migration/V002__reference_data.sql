-- MES-9 US10: routing reference data (interim master — Settings submodule).
-- Work centres/machines, labour plan types, labour codes, route types,
-- significant-process types, suppliers. All organisation-scoped (§IX).
-- Protected defaults are seeded for the dev org only (00000000-0000-0000-0000-000000000001);
-- per-org seeding at org provisioning is a documented follow-up (see spec assumptions / DEF-004).

-- ── Work centre / machine ───────────────────────────────────────────────────
CREATE TABLE routing.work_centre (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_work_centre PRIMARY KEY (id),
    CONSTRAINT uq_work_centre_org_code UNIQUE (org_id, code)
);

-- ── Labour plan type (extensible: Machine / People / OSP) ────────────────────
CREATE TABLE routing.labour_plan_type (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    seeded      BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_labour_plan_type PRIMARY KEY (id),
    CONSTRAINT uq_labour_plan_type_org_code UNIQUE (org_id, code)
);

-- ── Labour code ──────────────────────────────────────────────────────────────
CREATE TABLE routing.labour_code (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id              UUID         NOT NULL,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    labour_plan_type_id UUID         REFERENCES routing.labour_plan_type (id),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    modified_by         VARCHAR(255) NOT NULL,
    modified_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_labour_code PRIMARY KEY (id),
    CONSTRAINT uq_labour_code_org_code UNIQUE (org_id, code)
);

-- ── Route type (seeded protected Standard; alternates NPI/FAI/Process Improvement) ─
CREATE TABLE routing.route_type (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    is_standard BOOLEAN      NOT NULL DEFAULT FALSE,
    seeded      BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_route_type PRIMARY KEY (id),
    CONSTRAINT uq_route_type_org_code UNIQUE (org_id, code)
);
-- At most one Standard route TYPE per org.
CREATE UNIQUE INDEX uq_route_type_one_standard
    ON routing.route_type (org_id) WHERE is_standard;

-- ── Significant-process type (each with a required approver role — FR-024) ────
CREATE TABLE routing.significant_process_type (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                UUID         NOT NULL,
    code                  VARCHAR(50)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    required_approver_role VARCHAR(100) NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by            VARCHAR(255) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    modified_by           VARCHAR(255) NOT NULL,
    modified_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_significant_process_type PRIMARY KEY (id),
    CONSTRAINT uq_significant_process_type_org_code UNIQUE (org_id, code)
);

-- ── Supplier (interim OSP supplier list — FR-009a, DEF-002) ──────────────────
CREATE TABLE routing.supplier (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_supplier PRIMARY KEY (id),
    CONSTRAINT uq_supplier_org_code UNIQUE (org_id, code)
);

-- ── Seed protected defaults for the dev org only ─────────────────────────────
-- Production: org-provisioning (platform-service) must seed these per new org.
INSERT INTO routing.route_type (org_id, code, name, is_standard, seeded, created_by, created_at, modified_by, modified_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'STANDARD', 'Standard', TRUE, TRUE,
        'migration', NOW(), 'migration', NOW());

INSERT INTO routing.labour_plan_type (org_id, code, name, seeded, created_by, created_at, modified_by, modified_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'MACHINE', 'Machine', TRUE, 'migration', NOW(), 'migration', NOW()),
       ('00000000-0000-0000-0000-000000000001', 'PEOPLE',  'People',  TRUE, 'migration', NOW(), 'migration', NOW()),
       ('00000000-0000-0000-0000-000000000001', 'OSP',     'OSP',     TRUE, 'migration', NOW(), 'migration', NOW());
