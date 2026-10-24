CREATE TABLE applications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company     TEXT        NOT NULL,
    job_title   TEXT        NOT NULL,
    posting_url TEXT,
    status      TEXT        NOT NULL DEFAULT 'SAVED',
    applied_on  DATE,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT applications_status_valid
        CHECK (status IN ('SAVED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'))
);

CREATE INDEX applications_user_id_idx ON applications (user_id);
