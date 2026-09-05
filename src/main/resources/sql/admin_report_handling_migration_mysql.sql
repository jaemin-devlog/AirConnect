-- Manual, reviewed migration before deploying the report-detail/CAS API.
-- Existing report reason/detail/source fields are unchanged. Historical handling
-- notes/replies are intentionally not copied from audit reasons into user-visible text.
ALTER TABLE user_reports
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN internal_memo VARCHAR(1000) NULL,
    ADD COLUMN reporter_reply VARCHAR(300) NULL,
    ADD COLUMN handled_by_user_id BIGINT NULL,
    ADD COLUMN completed_at DATETIME(6) NULL;

-- Legacy approximation: existing terminal rows have only their recorded updated_at.
-- Copy that actual stored value; do not fabricate an earlier completion timestamp.
UPDATE user_reports
SET completed_at = updated_at
WHERE status IN ('RESOLVED', 'REJECTED') AND completed_at IS NULL;

-- One USER_ACTION_APPLIED row preserves its original USER target and all existing
-- user filters/action counts. The nullable link adds indexed report-specific lookup.
ALTER TABLE admin_audit_logs
    ADD COLUMN report_id BIGINT NULL,
    ADD INDEX idx_admin_audit_report_created (report_id, created_at);

-- No new enum value, automatic purge, user/report FK, or action deletion cascade is added.
