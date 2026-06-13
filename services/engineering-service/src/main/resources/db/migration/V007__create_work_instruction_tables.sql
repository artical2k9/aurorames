-- V007: Work Instructions module (MES-10).
-- Revision-controlled, step-based instruction documents with media, skill requirements,
-- and append-only electronic signatures. Revision lifecycle mirrors the ECO/BOM pattern
-- (DRAFT -> PENDING_APPROVAL -> APPROVED).
--
-- Envers: revinfo + revinfo_seq already exist (created in V004). New @Audited entities
-- (work_instruction, work_instruction_revision, work_instruction_step, wi_media_attachment,
-- wi_skill_requirement) get _aud tables here with REVEND/REVEND_TSTMP inline
-- (ValidityAuditStrategy — ERR-MES-057). wi_electronic_signature is append-only and is NOT
-- Envers-audited, so it has no _aud table.

-- ── work_instruction (root) ──────────────────────────────────────────────────
CREATE TABLE engineering.work_instruction (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    identifier  VARCHAR(40)  NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT false,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by VARCHAR(255) NOT NULL,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_work_instruction        PRIMARY KEY (id),
    CONSTRAINT uq_work_instruction_ident  UNIQUE (org_id, identifier)
);

-- ── work_instruction_revision ────────────────────────────────────────────────
CREATE TABLE engineering.work_instruction_revision (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    work_instruction_id  UUID         NOT NULL,
    revision             INTEGER      NOT NULL,
    revision_status      VARCHAR(20)  NOT NULL,
    title                VARCHAR(200) NOT NULL,
    description          TEXT,
    part_context         VARCHAR(100),
    reason_for_revision  VARCHAR(500),
    custom_fields        JSONB,
    submitted_by         VARCHAR(255),
    submitted_at         TIMESTAMPTZ,
    approved_by          VARCHAR(255),
    approved_at          TIMESTAMPTZ,
    rejected_by          VARCHAR(255),
    rejected_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(500),
    created_by           VARCHAR(255) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by          VARCHAR(255) NOT NULL,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_wi_revision      PRIMARY KEY (id),
    CONSTRAINT fk_wir_instruction  FOREIGN KEY (work_instruction_id)
                                   REFERENCES engineering.work_instruction (id),
    CONSTRAINT uq_wir_revision     UNIQUE (work_instruction_id, revision)
);

-- One open draft per instruction.
CREATE UNIQUE INDEX uq_wir_one_draft
    ON engineering.work_instruction_revision (work_instruction_id)
    WHERE revision_status = 'DRAFT';

-- ── work_instruction_step ────────────────────────────────────────────────────
CREATE TABLE engineering.work_instruction_step (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    wi_revision_id UUID        NOT NULL,
    step_number   INTEGER      NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body_html     TEXT,
    custom_fields JSONB,
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by   VARCHAR(255) NOT NULL,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_wi_step       PRIMARY KEY (id),
    CONSTRAINT fk_wis_revision  FOREIGN KEY (wi_revision_id)
                                REFERENCES engineering.work_instruction_revision (id),
    CONSTRAINT uq_wis_step_no   UNIQUE (wi_revision_id, step_number)
);

-- ── wi_media_attachment ──────────────────────────────────────────────────────
CREATE TABLE engineering.wi_media_attachment (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    step_id       UUID         NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    caption       VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 0,
    storage_path  VARCHAR(500) NOT NULL,
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by   VARCHAR(255) NOT NULL,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_wi_media     PRIMARY KEY (id),
    CONSTRAINT fk_wim_step     FOREIGN KEY (step_id)
                               REFERENCES engineering.work_instruction_step (id)
);

-- ── wi_skill_requirement ─────────────────────────────────────────────────────
CREATE TABLE engineering.wi_skill_requirement (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    wi_revision_id UUID         NOT NULL,
    skill_id       UUID         NOT NULL,
    skill_code     VARCHAR(50)  NOT NULL,
    skill_name     VARCHAR(200) NOT NULL,
    created_by     VARCHAR(255) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by    VARCHAR(255) NOT NULL,
    modified_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_wi_skill_req     PRIMARY KEY (id),
    CONSTRAINT fk_wisr_revision    FOREIGN KEY (wi_revision_id)
                                   REFERENCES engineering.work_instruction_revision (id),
    CONSTRAINT uq_wisr_revision_skill UNIQUE (wi_revision_id, skill_id)
);

-- ── wi_electronic_signature (append-only; NOT Envers-audited) ─────────────────
CREATE TABLE engineering.wi_electronic_signature (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    wi_revision_id   UUID         NOT NULL,
    signer_user_id   VARCHAR(255) NOT NULL,
    signer_full_name VARCHAR(255) NOT NULL,
    signed_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    meaning          VARCHAR(50)  NOT NULL,
    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_wi_esign      PRIMARY KEY (id),
    CONSTRAINT fk_wies_revision FOREIGN KEY (wi_revision_id)
                                REFERENCES engineering.work_instruction_revision (id)
);

CREATE INDEX idx_wir_instruction ON engineering.work_instruction_revision (work_instruction_id);
CREATE INDEX idx_wis_revision    ON engineering.work_instruction_step (wi_revision_id);
CREATE INDEX idx_wim_step        ON engineering.wi_media_attachment (step_id);
CREATE INDEX idx_wisr_revision   ON engineering.wi_skill_requirement (wi_revision_id);
CREATE INDEX idx_wies_revision   ON engineering.wi_electronic_signature (wi_revision_id);

-- ── Envers _aud tables (REVEND/REVEND_TSTMP inline — ValidityAuditStrategy) ───
CREATE TABLE engineering.work_instruction_aud (
    id           UUID        NOT NULL,
    rev          INT4        NOT NULL,
    revtype      INT2,
    revend       INT4,
    revend_tstmp TIMESTAMPTZ,
    org_id       UUID,
    identifier   VARCHAR(40),
    deleted      BOOLEAN,
    created_by   VARCHAR(255),
    created_at   TIMESTAMPTZ,
    modified_by  VARCHAR(255),
    modified_at  TIMESTAMPTZ,
    CONSTRAINT pk_work_instruction_aud      PRIMARY KEY (id, rev),
    CONSTRAINT fk_work_instruction_aud_rev  FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev),
    CONSTRAINT fk_work_instruction_aud_revend FOREIGN KEY (revend) REFERENCES engineering.revinfo (rev)
);

CREATE TABLE engineering.work_instruction_revision_aud (
    id                  UUID        NOT NULL,
    rev                 INT4        NOT NULL,
    revtype             INT2,
    revend              INT4,
    revend_tstmp        TIMESTAMPTZ,
    work_instruction_id UUID,
    revision            INTEGER,
    revision_status     VARCHAR(20),
    title               VARCHAR(200),
    description         TEXT,
    part_context        VARCHAR(100),
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
    CONSTRAINT pk_wi_revision_aud       PRIMARY KEY (id, rev),
    CONSTRAINT fk_wir_aud_rev           FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev),
    CONSTRAINT fk_wir_aud_revend        FOREIGN KEY (revend) REFERENCES engineering.revinfo (rev)
);

CREATE TABLE engineering.work_instruction_step_aud (
    id             UUID        NOT NULL,
    rev            INT4        NOT NULL,
    revtype        INT2,
    revend         INT4,
    revend_tstmp   TIMESTAMPTZ,
    wi_revision_id UUID,
    step_number    INTEGER,
    title          VARCHAR(200),
    body_html      TEXT,
    custom_fields  JSONB,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ,
    modified_by    VARCHAR(255),
    modified_at    TIMESTAMPTZ,
    CONSTRAINT pk_wi_step_aud      PRIMARY KEY (id, rev),
    CONSTRAINT fk_wis_aud_rev      FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev),
    CONSTRAINT fk_wis_aud_revend   FOREIGN KEY (revend) REFERENCES engineering.revinfo (rev)
);

CREATE TABLE engineering.wi_media_attachment_aud (
    id            UUID        NOT NULL,
    rev           INT4        NOT NULL,
    revtype       INT2,
    revend        INT4,
    revend_tstmp  TIMESTAMPTZ,
    step_id       UUID,
    file_name     VARCHAR(255),
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    caption       VARCHAR(500),
    display_order INTEGER,
    storage_path  VARCHAR(500),
    created_by    VARCHAR(255),
    created_at    TIMESTAMPTZ,
    modified_by   VARCHAR(255),
    modified_at   TIMESTAMPTZ,
    CONSTRAINT pk_wi_media_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_wim_aud_rev      FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev),
    CONSTRAINT fk_wim_aud_revend   FOREIGN KEY (revend) REFERENCES engineering.revinfo (rev)
);

CREATE TABLE engineering.wi_skill_requirement_aud (
    id             UUID        NOT NULL,
    rev            INT4        NOT NULL,
    revtype        INT2,
    revend         INT4,
    revend_tstmp   TIMESTAMPTZ,
    wi_revision_id UUID,
    skill_id       UUID,
    skill_code     VARCHAR(50),
    skill_name     VARCHAR(200),
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ,
    modified_by    VARCHAR(255),
    modified_at    TIMESTAMPTZ,
    CONSTRAINT pk_wi_skill_req_aud   PRIMARY KEY (id, rev),
    CONSTRAINT fk_wisr_aud_rev       FOREIGN KEY (rev) REFERENCES engineering.revinfo (rev),
    CONSTRAINT fk_wisr_aud_revend    FOREIGN KEY (revend) REFERENCES engineering.revinfo (rev)
);
