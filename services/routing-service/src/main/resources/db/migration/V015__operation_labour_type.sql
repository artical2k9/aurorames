ALTER TABLE routing.route_operation
    ADD COLUMN labour_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE routing.route_operation_aud
    ADD COLUMN labour_type VARCHAR(20);
