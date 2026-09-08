ALTER TABLE member
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE member
    ADD CONSTRAINT chk_member_token_version_non_negative CHECK (token_version >= 0);
