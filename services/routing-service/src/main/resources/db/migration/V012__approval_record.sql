-- MES-9 US7/US8: immutable e-signature approval records for route and operation revisions.
-- Append-only audit rows (not Envers-audited — the row itself is the audit record, §V).

CREATE TABLE routing.approval_record (
    id                          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id                      UUID         NOT NULL,
    route_id                    UUID         NOT NULL REFERENCES routing.route (id) ON DELETE CASCADE,
    subject_type                VARCHAR(30)  NOT NULL,
    subject_id                  UUID         NOT NULL,
    revision                    INTEGER      NOT NULL,
    actor                       VARCHAR(255) NOT NULL,
    actor_role                  VARCHAR(100),
    meaning                     VARCHAR(100) NOT NULL,
    signed_at                   TIMESTAMPTZ  NOT NULL,
    significant_process_approver BOOLEAN     NOT NULL DEFAULT FALSE,
    significant_process_type_id UUID         REFERENCES routing.significant_process_type (id),
    governing_route_revision    INTEGER,
    created_by                  VARCHAR(255) NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_approval_record PRIMARY KEY (id)
);

CREATE INDEX idx_approval_record_route ON routing.approval_record (route_id);
CREATE INDEX idx_approval_record_subject
    ON routing.approval_record (subject_type, subject_id, revision);
