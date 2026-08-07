CREATE TABLE member_review (
    member_id         BIGINT PRIMARY KEY REFERENCES member (id),
    reviewer_id       BIGINT NOT NULL REFERENCES member (id),
    action            VARCHAR(20) NOT NULL CHECK (action IN ('APPROVED', 'REJECTED')),
    rejection_reason  TEXT,
    reviewed_at       TIMESTAMPTZ NOT NULL
);