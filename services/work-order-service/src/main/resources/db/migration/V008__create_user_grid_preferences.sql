CREATE TABLE work_order.user_grid_preferences (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id        UUID         NOT NULL,
    user_id       VARCHAR(255) NOT NULL,
    module_key    VARCHAR(50)  NOT NULL,
    column_config JSONB        NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_grid_preferences        PRIMARY KEY (id),
    CONSTRAINT uq_user_grid_preferences_key    UNIQUE (org_id, user_id, module_key)
);

CREATE INDEX idx_user_grid_preferences_lookup ON work_order.user_grid_preferences (org_id, user_id);
