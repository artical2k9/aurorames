-- V003: Centralised Unit of Measure master data table.
-- All modules reference this shared list rather than maintaining free-text UoM fields.

CREATE TABLE platform.unit_of_measure (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL,
    iso_code    VARCHAR(20),
    name        VARCHAR(200) NOT NULL,
    category    VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255) NOT NULL DEFAULT 'system',
    modified_by VARCHAR(255) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_uom_code UNIQUE (code)
);

CREATE INDEX idx_uom_category ON platform.unit_of_measure (category);
CREATE INDEX idx_uom_active   ON platform.unit_of_measure (active);
