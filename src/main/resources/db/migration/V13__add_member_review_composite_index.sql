CREATE INDEX idx_member_review_member_reviewed
    ON member_review (member_id, reviewed_at DESC, id DESC);
