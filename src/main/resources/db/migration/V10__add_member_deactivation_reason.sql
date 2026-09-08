ALTER TABLE member
    ADD COLUMN deactivation_reason VARCHAR(30);

ALTER TABLE member
    ADD CONSTRAINT ck_member_deactivation_reason
        CHECK (deactivation_reason IS NULL
            OR deactivation_reason IN ('SELF_WITHDRAWAL', 'ADMIN_EXPULSION'));
