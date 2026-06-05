-- V002: Add user_grid_preferences table to platform schema.

CREATE TABLE platform.user_grid_preferences (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id        UUID         NOT NULL,
    user_id       VARCHAR(255) NOT NULL,
    module_key    VARCHAR(50)  NOT NULL,
    column_config JSONB        NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ugp_org_user_module UNIQUE (org_id, user_id, module_key)
);

CREATE INDEX idx_ugp_org_user ON platform.user_grid_preferences (org_id, user_id);
