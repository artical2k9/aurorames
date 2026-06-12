ALTER TABLE inventory.bom_line
    ADD COLUMN custom_fields jsonb;

ALTER TABLE inventory.bom_line_aud
    ADD COLUMN custom_fields jsonb;
